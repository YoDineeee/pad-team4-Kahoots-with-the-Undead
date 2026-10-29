// ============================================================================
// START HERE: npm start  ->  node src/server.js
// ============================================================================

import { Registry } from './registry.js';
import { createApp } from './app.js';
import { log } from './log.js';

const PORT = Number(process.env.PORT || 8761);
// An instance that sent no heartbeat for this long is considered dead.
// Services send one every 10 s, so 30 s = three missed heartbeats in a row.
const INSTANCE_TTL_MS = Number(process.env.INSTANCE_TTL_MS || 30000);

const registry = new Registry();
const app = createApp(registry);

const server = app.listen(PORT, () => {
  log('INFO', `Service Discovery listening on http://localhost:${PORT} (instance TTL ${INSTANCE_TTL_MS} ms)`);
});

// Every 5 seconds: forget the instances that stopped sending heartbeats.
setInterval(() => registry.removeSilentInstances(INSTANCE_TTL_MS), 5000);

// "docker stop" sends SIGTERM: stop accepting requests, then exit.
process.on('SIGTERM', () => {
  log('INFO', 'Shutting down');
  server.close(() => process.exit(0));
});
