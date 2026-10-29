// ============================================================================
// HEALTH CHECK:  GET /health
// Asks every instance of every service "are you alive?" on its health path
// (Spring Boot: /actuator/health, Flask: /health - see the routing table).
// A service is UP if at least one of its instances is UP.
//   all UP      -> 200 {"status":"UP",       "services":{"player-service":"UP", ...}, "instances":{...}}
//   one is DOWN -> 503 {"status":"DEGRADED", "services":{"player-service":"DOWN", ...}, "instances":{...}}
// ============================================================================

async function isUp(healthUrl) {
  try {
    const answer = await fetch(healthUrl, { signal: AbortSignal.timeout(2000) });
    return answer.ok; // true for any 2xx status
  } catch {
    return false; // connection refused, unknown host or timeout
  }
}

export function createHealthCheck(routes, registry) {
  // Several routes point to the same service; we only want to ask each service once.
  // { "player-service": "/actuator/health", "base-service": "/health", ... }
  const healthPaths = {};
  for (const route of routes) healthPaths[route.service] = route.healthPath;

  return async function healthCheck(req, res) {
    const services = {};
    const instances = {};

    // Ask everybody at the same time, so one slow instance doesn't add up.
    await Promise.all(Object.keys(healthPaths).map(async (service) => {
      const list = registry.instancesOf(service);
      const results = await Promise.all(list.map((i) => isUp(i.url + healthPaths[service])));
      instances[service] = list.map((instance, n) => ({ instance_id: instance.id, status: results[n] ? 'UP' : 'DOWN' }));
      services[service] = results.includes(true) ? 'UP' : 'DOWN';
    }));

    const allUp = Object.values(services).every((status) => status === 'UP');
    res.status(allUp ? 200 : 503).json({ status: allUp ? 'UP' : 'DEGRADED', gateway: 'UP', services, instances });
  };
}
