// ============================================================================
// SERVICE DISCOVERY - the HTTP endpoints
//
//   POST   /register                                  "I'm here" (on startup, then every 10 s as heartbeat)
//   DELETE /services/:service/instances/:instanceId   "I'm leaving" (on shutdown)
//   GET    /services                                  who is running where (the gateway asks every 3 s)
//   GET    /status                                    is the Service Discovery itself OK?
//   GET    /health                                    liveness, for Docker
// ============================================================================

import express from 'express';
import { log } from './log.js';

export function createApp(registry) {
  const app = express();
  const startedAt = Date.now();
  app.use(express.json());

  // One log line per request. Heartbeats and status checks come every few seconds,
  // so they are not logged (they would flood the log).
  app.use((req, res, next) => {
    res.on('finish', () => {
      const quiet = ['/register', '/status', '/health', '/services'].includes(req.path) && res.statusCode < 400;
      if (!quiet) log(res.statusCode >= 400 ? 'WARN' : 'INFO', `${req.method} ${req.originalUrl} -> ${res.statusCode}`);
    });
    next();
  });

  app.post('/register', (req, res) => {
    const { service, instance_id, url } = req.body || {};
    if (!service || !instance_id || !url) {
      return res.status(400).json({ error: 'INVALID_REGISTRATION', message: 'service, instance_id and url are required' });
    }
    const isNew = registry.register({ service, instance_id, url });
    res.status(isNew ? 201 : 200).json({ registered: true, service, instance_id, url });
  });

  app.delete('/services/:service/instances/:instanceId', (req, res) => {
    const { service, instanceId } = req.params;
    if (!registry.deregister(service, instanceId)) {
      return res.status(404).json({ error: 'INSTANCE_NOT_FOUND', message: `${service}/${instanceId} is not registered` });
    }
    res.json({ deregistered: true, service, instance_id: instanceId });
  });

  app.get('/services', (req, res) => {
    res.json(registry.list());
  });

  app.get('/status', (req, res) => {
    res.json({
      service: 'service-discovery',
      status: 'UP',
      uptime_seconds: Math.round((Date.now() - startedAt) / 1000),
      registered_instances: registry.countInstances(),
    });
  });

  app.get('/health', (req, res) => res.json({ status: 'UP' }));

  app.use((req, res) => {
    res.status(404).json({ error: 'ROUTE_NOT_FOUND', message: `No route for ${req.method} ${req.originalUrl}` });
  });

  return app;
}
