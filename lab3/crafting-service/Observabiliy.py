"""Lab 3: logs, GET /status and registration with the Service Discovery.

app.py uses two functions from here:

    log = init_observability(app, "crafting-service")    # request logs + GET /status
    start_registration("crafting-service", 5008)         # register with the Service Discovery

Every log line shows the request id the gateway gave the request, so you can follow
one request from the gateway's log into this service's log:

    2026-10-28 15:04:05,123 INFO  [9f1c...] crafting-service : --> POST /api/crafting/recipes
    2026-10-28 15:04:05,130 INFO  [9f1c...] crafting-service : Recipe created: wooden-plank
    2026-10-28 15:04:05,131 INFO  [9f1c...] crafting-service : <-- POST /api/crafting/recipes 201 (8 ms)

(The Base Service has its own copy of this file on purpose - no shared code at runtime.)
"""
import atexit
import json
import logging
import os
import signal
import socket
import sys
import threading
import time
import urllib.request
import uuid

from flask import g, has_request_context, jsonify, request


# ---------------------------------------------------------------------------
# Logs
# ---------------------------------------------------------------------------
class AddRequestId(logging.Filter):
    """Puts the current request id into every log line (empty outside a request)."""

    def filter(self, record):
        record.request_id = g.get("request_id", "") if has_request_context() else ""
        return True


def setup_logging(service_name):
    handler = logging.StreamHandler(sys.stdout)
    handler.addFilter(AddRequestId())
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)-5s [%(request_id)s] %(name)s : %(message)s"))
    logging.getLogger().handlers = [handler]
    logging.getLogger().setLevel(logging.INFO)
    logging.getLogger("werkzeug").setLevel(logging.WARNING)  # we log every request ourselves
    return logging.getLogger(service_name)


def instance_id(service_name):
    """INSTANCE_ID, or "<service>-<hostname>" (in Docker the hostname is the container id)."""
    return os.environ.get("INSTANCE_ID") or f"{service_name}-{socket.gethostname()}"


# ---------------------------------------------------------------------------
# Request logs + GET /status
# ---------------------------------------------------------------------------
def init_observability(app, service_name):
    log = setup_logging(service_name)
    my_id = instance_id(service_name)
    started_at = time.time()
    counter = {"requests": 0}

    @app.before_request
    def request_arrived():
        g.request_id = request.headers.get("X-Request-Id") or str(uuid.uuid4())
        g.started = time.time()
        if request.path not in ("/status", "/health"):  # don't count/log the health checks
            counter["requests"] += 1
            log.info("--> %s %s", request.method, request.full_path.rstrip("?"))

    @app.after_request
    def request_answered(response):
        response.headers["X-Request-Id"] = g.request_id
        response.headers["X-Instance-Id"] = my_id
        if request.path not in ("/status", "/health"):
            took_ms = round((time.time() - g.started) * 1000)
            log.info("<-- %s %s %s (%s ms)", request.method, request.path, response.status_code, took_ms)
        return response

    @app.get("/status")
    def status():
        """Is this instance alive? Same answer shape in every service."""
        return jsonify({
            "service": service_name,
            "instance_id": my_id,
            "status": "UP",
            "uptime_seconds": round(time.time() - started_at),
            "requests_total": counter["requests"],
        }), 200

    return log


# ---------------------------------------------------------------------------
# Service Discovery registration
# ---------------------------------------------------------------------------
def call(method, url, body):
    """Sends JSON, returns the HTTP status code."""
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=3) as response:
        return response.status


def start_registration(service_name, port):
    """Registers on startup, sends a heartbeat every 10 s, deregisters on exit.

    Does nothing when SERVICE_DISCOVERY_URL is not set (laptop, tests).
    """
    log = logging.getLogger(service_name)
    discovery = os.environ.get("SERVICE_DISCOVERY_URL", "")
    if not discovery:
        log.info("SERVICE_DISCOVERY_URL not set: not registering")
        return

    my_id = instance_id(service_name)
    my_url = f"http://{socket.gethostbyname(socket.gethostname())}:{port}"  # this container's IP
    every_seconds = float(os.environ.get("SD_HEARTBEAT_SECONDS", 10))

    def heartbeat_forever():
        while True:
            try:
                status = call("POST", f"{discovery}/register",
                              {"service": service_name, "instance_id": my_id, "url": my_url})
                if status == 201:
                    log.info("Registered with the Service Discovery as %s at %s", my_id, my_url)
            except OSError as exc:  # connection refused, timeout ...
                log.warning("Could not reach the Service Discovery (%s), retrying in %ss", exc, every_seconds)
            time.sleep(every_seconds)

    def deregister():
        try:
            call("DELETE", f"{discovery}/services/{service_name}/instances/{my_id}", {})
            log.info("Deregistered from the Service Discovery")
        except OSError as exc:
            log.warning("Could not deregister: %s", exc)

    # daemon=True: the thread doesn't keep the program alive when it should exit
    threading.Thread(target=heartbeat_forever, daemon=True).start()
    # "docker stop" sends SIGTERM. Turning it into a normal exit makes Python run the atexit functions.
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    atexit.register(deregister)
    