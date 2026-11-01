"""Lab 3: GET /status, request/instance id headers and Service Discovery registration."""
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

import app as base_app
import observability

PLAYER = {"Authorization": "Bearer player-token"}


@pytest.fixture
def client():
    base_app.reset_state()
    return base_app.app.test_client()


def test_status_counts_requests_but_not_health_checks(client):
    before = client.get("/status").get_json()["requests_total"]
    client.get("/api/bases/b-404", headers=PLAYER)          # a 404, counted
    client.get("/health")                       # health check, not counted
    body = client.get("/status").get_json()
    assert body["service"] == "base-service"
    assert body["status"] == "UP"
    assert body["instance_id"].startswith("base-service-")
    assert body["requests_total"] == before + 1


def test_every_response_has_request_and_instance_id(client):
    res = client.get("/api/bases/b-404", headers={**PLAYER, "X-Request-Id": "abc-123"})
    assert res.headers["X-Request-Id"] == "abc-123"
    assert res.headers["X-Instance-Id"].startswith("base-service-")


def test_registers_with_service_discovery_and_sends_heartbeats(monkeypatch):
    calls = []

    class FakeDiscovery(BaseHTTPRequestHandler):
        def do_POST(self):
            calls.append((self.path, json.loads(self.rfile.read(int(self.headers["Content-Length"])))))
            self.send_response(201 if len(calls) == 1 else 200)
            self.end_headers()

        def log_message(self, *args):
            pass

    server = HTTPServer(("127.0.0.1", 0), FakeDiscovery)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    monkeypatch.setenv("SERVICE_DISCOVERY_URL", f"http://127.0.0.1:{server.server_port}")
    monkeypatch.setenv("SD_HEARTBEAT_SECONDS", "0.1")
    monkeypatch.setattr(observability.signal, "signal", lambda *a: None)   # don't touch pytest's handlers
    monkeypatch.setattr(observability.atexit, "register", lambda f: None)

    observability.start_registration("base-service", 1234)
    deadline = time.time() + 3
    while len(calls) < 2 and time.time() < deadline:
        time.sleep(0.05)
    server.shutdown()

    assert len(calls) >= 2                       # registration + at least one heartbeat
    path, body = calls[0]
    assert path == "/register"
    assert body["service"] == "base-service"
    assert body["url"].endswith(":1234")

    