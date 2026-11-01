// ============================================================================
// CIRCUIT BREAKER (grade 7)
//
// Rule: if ONE instance fails 3 times within (task timeout limit * 3.5), the breaker TRIPS.
// All endpoints of that instance count together (/api/zombies/types and /api/zombies/types/x
// are the same instance).
//
// A failure is: the instance is unreachable (502), too slow (504) or answered 5xx.
// A 4xx answer is NOT a failure: "zombie type not found" means the service works fine.
//
// When it trips, it is logged as an ERROR. (Removing the instance would be grade 10.)
// ============================================================================

import { log } from './log.js';

export class CircuitBreaker {
  constructor(timeoutMs, now = () => Date.now()) {
    this.maxFailures = 3;
    this.windowMs = timeoutMs * 3.5;
    this.now = now;
    this.failures = {}; // { "zombie-service/zombie-1": [time of failure, time of failure, ...] }
    this.trips = [];    // every trip so far, shown on GET /status
  }

  recordFailure(service, instanceId, reason) {
    const key = `${service}/${instanceId}`;
    const now = this.now();

    // Keep only the failures inside the window, then add this one.
    const recent = (this.failures[key] || []).filter((time) => now - time < this.windowMs);
    recent.push(now);
    this.failures[key] = recent;

    log('WARN', `Call to ${key} failed (${reason}) - ${recent.length}/${this.maxFailures} failures in the last ${this.windowMs} ms`);

    if (recent.length >= this.maxFailures) {
      log('ERROR', `CIRCUIT BREAKER TRIPPED for ${key}: ${recent.length} failures within ${this.windowMs} ms`);
      this.trips.push({ instance: key, failures: recent.length, at: new Date(now).toISOString() });
      this.failures[key] = []; // start counting again
    }
  }
}
