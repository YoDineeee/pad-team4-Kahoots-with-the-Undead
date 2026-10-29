"""API Gateway: /{service}/{path} -> round-robin over healthy instances of {service}."""
import logging
import os
import socket
import time

from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse

from common.discovery import DiscoveryClient, ServiceCaller
from common.logging_setup import setup_logging

PORT = int(os.getenv("PORT", "8080"))
INSTANCE = os.getenv("INSTANCE_ID", socket.gethostname())
SD_URL = os.getenv("SD_URL", "http://service-discovery:8500")
TASK_TIMEOUT = float(os.getenv("TASK_TIMEOUT", "5"))

setup_logging("gateway", INSTANCE)
log = logging.getLogger("gateway")
discovery = DiscoveryClient(SD_URL)
caller = ServiceCaller(discovery, TASK_TIMEOUT, os.getenv("BREAKER_MODE", "log"))
app = FastAPI(title="gateway")
started = time.time()

HOP_BY_HOP = {"host", "content-length", "connection", "transfer-encoding"}


@app.get("/status")
async def status():
    return {"status": "UP", "service": "gateway", "instance": INSTANCE, "uptime_s": round(time.time() - started)}


@app.api_route("/{service}/{path:path}", methods=["GET", "POST", "PUT", "PATCH", "DELETE"])
async def proxy(service: str, path: str, request: Request):
    log.info("incoming %s /%s/%s", request.method, service, path)
    body = await request.body()
    headers = {k: v for k, v in request.headers.items() if k.lower() not in HOP_BY_HOP}
    try:
        resp = await caller.call(service, request.method, f"/{path}",
                                 params=dict(request.query_params), content=body, headers=headers)
    except LookupError:
        return JSONResponse({"error": f"service '{service}' unavailable"}, status_code=503)
    except Exception as exc:
        log.error("upstream failure for /%s/%s: %s", service, path, exc)
        return JSONResponse({"error": "upstream failure"}, status_code=502)
    out_headers = {k: v for k, v in resp.headers.items() if k.lower() not in HOP_BY_HOP | {"content-encoding"}}
    return Response(content=resp.content, status_code=resp.status_code, headers=out_headers)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=PORT)
    