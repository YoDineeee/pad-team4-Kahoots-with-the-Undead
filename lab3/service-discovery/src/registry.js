// ============================================================================
// THE REGISTRY: which instances of which service are running, and where.
//
//   services = Map {
//     "zombie-service" => Map {
//       "zombie-1" => { instance_id: "zombie-1", url: "http://172.18.0.7:8081", last_heartbeat: 1730120000000 },
//       "zombie-2" => { ... },
//     },
//     "base-service" => Map { ... },
//   }
//
// The life of an instance:
//   1. starts            -> register()       (it is added)
//   2. every 10 seconds  -> register() again (= heartbeat: "I'm still alive")
//   3a. clean shutdown   -> deregister()     (it is removed at once)
//   3b. crash            -> no more heartbeats -> removeSilentInstances() removes it after the TTL
// ============================================================================

import { log } from './log.js';

export class Registry {
  constructor(now = () => Date.now()) {
    this.services = new Map();
    this.now = now; // the tests replace the clock, so they don't have to wait 30 seconds
  }

  /** Adds an instance, or refreshes its heartbeat. Returns true if it is new. */
  register({ service, instance_id, url }) {
    if (!this.services.has(service)) {
      this.services.set(service, new Map());
    }
    const instances = this.services.get(service);
    const isNew = !instances.has(instance_id);

    instances.set(instance_id, { instance_id, url, last_heartbeat: this.now() });

    if (isNew) log('INFO', `Registered ${service}/${instance_id} at ${url} (${instances.size} instance(s) now)`);
    return isNew;
  }

  /** Removes an instance. Returns false if it wasn't registered. */
  deregister(service, instanceId) {
    const instances = this.services.get(service);
    if (!instances || !instances.delete(instanceId)) return false;

    if (instances.size === 0) this.services.delete(service);
    log('INFO', `Deregistered ${service}/${instanceId}`);
    return true;
  }

  /** Removes every instance that hasn't sent a heartbeat for longer than ttlMs (it probably crashed). */
  removeSilentInstances(ttlMs) {
    for (const [service, instances] of this.services) {
      for (const [instanceId, instance] of instances) {
        const silentForMs = this.now() - instance.last_heartbeat;
        if (silentForMs > ttlMs) {
          instances.delete(instanceId);
          log('WARN', `Removed ${service}/${instanceId}: no heartbeat for ${silentForMs} ms`);
        }
      }
      if (instances.size === 0) this.services.delete(service);
    }
  }

  /** { "zombie-service": [ { instance_id, url, last_heartbeat }, ... ], ... } */
  list() {
    const result = {};
    for (const [service, instances] of this.services) {
      result[service] = [...instances.values()].map((i) => ({
        instance_id: i.instance_id,
        url: i.url,
        last_heartbeat: new Date(i.last_heartbeat).toISOString(),
      }));
    }
    return result;
  }

  countInstances() {
    let count = 0;
    for (const instances of this.services.values()) count += instances.size;
    return count;
  }
}
