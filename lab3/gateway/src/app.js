// ============================================================================
// THE GATEWAY APPLICATION
//
// Every request passes through these steps, top to bottom:
//   1. read the body as raw bytes (we don't need to understand it, only forward it)
//   2. give the request an id, count it and log it
//   3. gateway's own endpoints:  GET /,  GET /health,  GET /status
//   4. one proxy per entry of the routing table (round robin + circuit breaker, see proxy.js)
//   5. nothing matched -> 404
// ============================================================================

import express from 'express';
import { randomUUID } from 'node:crypto';
import { ROUTES, fixedServiceUrls } from './config.js';
import { createProxy } from './proxy.js';
import { createHealthCheck } from './health.js';
import { ServiceRegistry } from './registry.js';
import { RoundRobin } from './loadBalancer.js';
import { CircuitBreaker } from './circuitBreaker.js';
import { log } from './log.js';

export function createApp(config) {
  const app = express();
  const timeoutMs = config.timeoutMs ?? 10000;

  const registry = new ServiceRegistry(config.serviceDiscoveryUrl, fixedServiceUrls(config));
  const balancer = new RoundRobin();
  const breaker = new CircuitBreaker(timeoutMs);
  app.locals.registry = registry; // server.js starts it; the tests use it too

  // Without a Service Discovery, a service with no URL is simply not routed.
  const routes = ROUTES.filter((route) => config.serviceDiscoveryUrl || registry.instancesOf(route.service).length > 0);

  const startedAt = Date.now();
  let requestsTotal = 0;

  // 1. Keep the body as raw bytes (a Buffer), whatever the content type.
  app.use(express.raw({ type: '*/*', limit: '1mb' }));

  // 2. Request id + one log line per request.
  app.use((req, res, next) => {
    req.requestId = req.get('x-request-id') || randomUUID();
    res.set('x-request-id', req.requestId);
    requestsTotal += 1;

    const start = Date.now();
    res.on('finish', () => {
      const instance = res.get('x-instance-id') ? ` by ${res.get('x-instance-id')}` : '';
      const level = res.statusCode >= 500 ? 'ERROR' : res.statusCode >= 400 ? 'WARN' : 'INFO';
      log(level, `${req.method} ${req.originalUrl} -> ${res.statusCode} (${Date.now() - start} ms)${instance} [request ${req.requestId}]`);
    });
    next(); // continue with the next step
  });

  // 3. The gateway's own endpoints.
  app.get('/', (req, res) => {
    res.json({
      name: 'undead-gateway',
      routes: routes.map((r) => ({ prefix: r.prefix, service: r.service, instances: registry.instancesOf(r.service).length })),
    });
  });

  app.get('/health', createHealthCheck(routes, registry));

  app.get('/status', (req, res) => {
    res.json({
      service: 'gateway',
      status: 'UP',
      uptime_seconds: Math.round((Date.now() - startedAt) / 1000),
      requests_total: requestsTotal,
      instances: registry.instances,
      round_robin_counters: balancer.counters,
      circuit_breaker: { max_failures: breaker.maxFailures, window_ms: breaker.windowMs, trips: breaker.trips },
    });
  });

  // 4. The routing: app.use('/api/players', ...) matches /api/players AND everything below it.
  for (const route of routes) {
    app.use(route.prefix, createProxy(route, { registry, balancer, breaker, timeoutMs }));
  }

  // 5. No route matched.
  app.use((req, res) => {
    res.status(404).json({ error: 'ROUTE_NOT_FOUND', message: `No service handles ${req.method} ${req.originalUrl}` });
  });

  return app;
}
