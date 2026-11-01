// ============================================================================
// ROUND-ROBIN LOAD BALANCER (grade 6)
//
// Every service has its own counter. With instances [A, B, C]:
//   request 0 -> A,  1 -> B,  2 -> C,  3 -> A,  4 -> B ...     (counter % number of instances)
// ============================================================================

export class RoundRobin {
  constructor() {
    this.counters = {}; // { "zombie-service": 7, ... } = how many requests each service got
  }

  /** The instance that gets the next request, or null if there is none. */
  pick(service, instances) {
    if (instances.length === 0) return null;
    const count = this.counters[service] || 0;
    this.counters[service] = count + 1;
    return instances[count % instances.length];
  }
}
