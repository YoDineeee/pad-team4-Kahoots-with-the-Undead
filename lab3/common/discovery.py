"""Service Discovery client + round-robin caller (Grades 4, 5, 6)."""
import asyncio
import itertools
import logging
import os
import socket
import threading
import time
from typing import Optional

import httpx

from .resilience import CircuitBreaker

log = logging.getLogger("discovery")


def local_ip() -> str:
    """Container IP; resolvable by peers on the compose network."""
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return "127.0.0.1"


class DiscoveryClient:
    def __init__(self, sd_url: str):
        self.sd_url = sd_url.rstrip("/")

    async def register(self, name: str, instance_id: str, host: str, port: int, attempts: int = 15) -> bool:
        payload = {"name": name, "instance_id": instance_id, "host": host, "port": port}
        async with httpx.AsyncClient(timeout=3) as c:
            for n in range(1, attempts + 1):
                try:
                    r = await c.post(f"{self.sd_url}/register", json=payload)
                    r.raise_for_status()
                    log.info("registered with Service Discovery as %s/%s at %s:%d", name, instance_id, host, port)
                    return True
                except Exception as exc:
                    log.warning("registration attempt %d/%d failed: %s", n, attempts, exc)
                    await asyncio.sleep(2)
        log.error("could not register with Service Discovery after %d attempts", attempts)
        return False

    async def deregister(self, name: str, instance_id: str) -> None:
        try:
            async with httpx.AsyncClient(timeout=3) as c:
                await c.post(f"{self.sd_url}/deregister", json={"name": name, "instance_id": instance_id})
            log.info("deregistered %s/%s", name, instance_id)
        except Exception as exc:
            log.warning("deregistration failed: %s", exc)

    async def instances(self, name: str) -> list:
        async with httpx.AsyncClient(timeout=3) as c:
            r = await c.get(f"{self.sd_url}/services/{name}")
            r.raise_for_status()
            return r.json()


class ServiceCaller:
    """Resolves a service name to instances, balances round-robin, and applies the circuit breaker.
    Used by the gateway AND by any service that calls another service."""

    def __init__(self, discovery: DiscoveryClient, task_timeout: float, breaker_mode: str):
        self.discovery = discovery
        self.timeout = task_timeout
        self.breaker = CircuitBreaker(task_timeout, mode=breaker_mode, on_trip=self._deregister_on_trip)
        self._counters = {}                      # service name -> itertools.count (round-robin state)
        self._counter_lock = threading.Lock()
        self._cache = {}                         # service name -> (fetched_at, instances)
        self._cache_ttl = float(os.getenv("DISCOVERY_CACHE_TTL", "2"))
        self._instance_info = {}                 # instance key -> (name, id), for deregistration
        self._loop: Optional[asyncio.AbstractEventLoop] = None

    # --- Grade 9: breaker callback removes the instance from Service Discovery
    def _deregister_on_trip(self, instance_key: str) -> None:
        name, inst_id = self._instance_info.get(instance_key, (None, None))
        if name and self._loop:
            asyncio.run_coroutine_threadsafe(self.discovery.deregister(name, inst_id), self._loop)
            self._cache.pop(name, None)

    async def _resolve(self, name: str) -> list:
        cached = self._cache.get(name)
        if cached and time.monotonic() - cached[0] < self._cache_ttl:
            return cached[1]
        items = await self.discovery.instances(name)      # dynamic add/remove happens here
        self._cache[name] = (time.monotonic(), items)
        return items

    def _next_index(self, name: str) -> int:
        with self._counter_lock:
            counter = self._counters.setdefault(name, itertools.count())
            return next(counter)

    async def call(self, name: str, method: str, path: str, **kwargs) -> httpx.Response:
        self._loop = asyncio.get_running_loop()
        try:
            instances = await self._resolve(name)
        except Exception as exc:
            log.error("Service Discovery unreachable while resolving %s: %s", name, exc)
            raise
        instances = [i for i in instances
                     if not self.breaker.is_removed(f"{i['name']}/{i['instance_id']}")]
        if not instances:
            log.error("no available instances for service '%s'", name)
            raise LookupError(f"no available instances for {name}")

        start = self._next_index(name)                     # Round-robin: rotate the starting point
        last_exc: Exception = RuntimeError("unreachable")
        for attempt in range(len(instances)):              # on failure, fail over to the next instance
            inst = instances[(start + attempt) % len(instances)]
            ikey = f"{inst['name']}/{inst['instance_id']}"
            self._instance_info[ikey] = (inst["name"], inst["instance_id"])
            url = f"http://{inst['host']}:{inst['port']}{path}"
            log.info("-> %s %s (round-robin pick %s)", method, url, ikey)
            try:
                async with httpx.AsyncClient(timeout=self.timeout) as c:
                    resp = await c.request(method, url, **kwargs)
                if resp.status_code >= 500:
                    self.breaker.record_failure(ikey, f"HTTP {resp.status_code}")
                    last_exc = RuntimeError(f"{ikey} returned {resp.status_code}")
                    continue
                self.breaker.record_success(ikey)
                return resp
            except Exception as exc:
                self.breaker.record_failure(ikey, type(exc).__name__)
                last_exc = exc
        log.error("all instances of '%s' failed", name)
        raise last_exc

        