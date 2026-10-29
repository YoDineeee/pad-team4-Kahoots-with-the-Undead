"""Circuit breaker (Grades 6 and 9).

Rule: `threshold` (3) failures inside a sliding window of `task_timeout * 3.5` seconds
trips the breaker for that instance.

mode="log"    -> Grade 6: only log "CIRCUIT TRIPPED"; traffic keeps flowing.
mode="remove" -> Grade 9: take the instance out of rotation and deregister it
                 from Service Discovery (callback `on_trip`).
"""
import logging
import threading
import time
from collections import defaultdict, deque
from typing import Callable, Optional

log = logging.getLogger("circuit-breaker")


class CircuitBreaker:
    def __init__(self, task_timeout: float, threshold: int = 3, mode: str = "log",
                 removal_seconds: float = 60.0, on_trip: Optional[Callable[[str], None]] = None):
        self.window = task_timeout * 3.5
        self.threshold = threshold
        self.mode = mode
        self.removal_seconds = removal_seconds
        self.on_trip = on_trip
        self._failures = defaultdict(deque)   # instance key -> failure timestamps
        self._removed = {}                    # instance key -> removed-until timestamp
        self._lock = threading.Lock()

    def record_failure(self, instance_key: str, reason: str) -> bool:
        """Returns True if this failure tripped the breaker."""
        now = time.monotonic()
        with self._lock:
            q = self._failures[instance_key]
            q.append(now)
            while q and now - q[0] > self.window:      # drop failures outside the window
                q.popleft()
            count = len(q)
            log.warning("call to %s failed (%s) - %d/%d failures in %.1fs window",
                        instance_key, reason, count, self.threshold, self.window)
            if count < self.threshold:
                return False
            q.clear()

        log.error("CIRCUIT TRIPPED for %s: %d errors within %.1fs", instance_key, self.threshold, self.window)
        if self.mode == "remove":
            with self._lock:
                self._removed[instance_key] = now + self.removal_seconds
            log.error("REMOVING %s from rotation for %.0fs", instance_key, self.removal_seconds)
            if self.on_trip:
                try:
                    self.on_trip(instance_key)
                except Exception as exc:  # never let cleanup break the request path
                    log.error("on_trip callback failed for %s: %s", instance_key, exc)
        return True

    def record_success(self, instance_key: str) -> None:
        with self._lock:
            self._failures.pop(instance_key, None)

    def is_removed(self, instance_key: str) -> bool:
        if self.mode != "remove":
            return False
        with self._lock:
            until = self._removed.get(instance_key)
            if until is None:
                return False
            if time.monotonic() >= until:
                del self._removed[instance_key]
                log.info("removal period over for %s - instance eligible again", instance_key)
                return False
            return True


            