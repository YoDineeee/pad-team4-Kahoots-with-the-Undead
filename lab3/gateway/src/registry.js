// ============================================================================
// SERVICE REGISTRY - the gateway's answer to "where are the instances of zombie-service?"
//
//   instances = {
//     "zombie-service": [ { id: "zombie-1", url: "http://172.18.0.7:8081" },
//                         { id: "zombie-2", url: "http://172.18.0.8:8081" } ],
//     ...
//   }
//
// Two modes:
//   - WITH a Service Discovery (Docker): every 3 s we download GET /services and replace the list.
//     So an instance that starts appears within seconds, one that stops disappears (grade 5).
//     If the Service Discovery doesn't answer, we simply keep the last list.
//   - WITHOUT one (laptop, unit tests): one fixed instance per service, from the *_SERVICE_URL variables.
// ============================================================================

import { log } from './log.js';

export class ServiceRegistry {
  constructor(discoveryUrl, fixedUrls = {}) {
    this.discoveryUrl = discoveryUrl;
    this.instances = {};

    if (!discoveryUrl) {
      for (const [service, url] of Object.entries(fixedUrls)) {
        if (url) this.instances[service] = [{ id: service, url }];
      }
    }
  }

  /** The instances of one service (an empty list if there are none). */
  instancesOf(service) {
    return this.instances[service] || [];
  }

  /** Downloads the current list from the Service Discovery. */
  async refresh() {
    if (!this.discoveryUrl) return;
    try {
      const response = await fetch(`${this.discoveryUrl}/services`, { signal: AbortSignal.timeout(2000) });
      const services = await response.json(); // { "zombie-service": [ { instance_id, url }, ... ] }

      const next = {};
      for (const [service, list] of Object.entries(services)) {
        next[service] = list.map((i) => ({ id: i.instance_id, url: i.url }));
      }
      this.logChanges(next);
      this.instances = next;
    } catch (error) {
      log('WARN', `Service Discovery unreachable (${error.message}), keeping the last known instances`);
    }
  }

  /** Logs which instances joined or left since the last refresh. */
  logChanges(next) {
    const names = (instances) => Object.entries(instances).flatMap(([s, list]) => list.map((i) => `${s}/${i.id}`));
    const before = names(this.instances);
    const after = names(next);
    for (const name of after) if (!before.includes(name)) log('INFO', `Instance joined: ${name}`);
    for (const name of before) if (!after.includes(name)) log('WARN', `Instance left: ${name}`);
  }

  /** Refreshes now and then every refreshMs. */
  start(refreshMs = 3000) {
    if (!this.discoveryUrl) return;
    log('INFO', `Asking the Service Discovery ${this.discoveryUrl} for instances every ${refreshMs} ms`);
    this.timer = setInterval(() => this.refresh(), refreshMs);
    return this.refresh();
  }

  stop() {
    clearInterval(this.timer);
  }
}
