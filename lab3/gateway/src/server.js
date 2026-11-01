// ============================================================================
// START HERE: npm start  ->  node src/server.js
// ============================================================================

import { loadConfig } from './config.js';
import { createApp } from './app.js';
import { startRegistration, stopRegistration } from './registrar.js';
import { log } from './log.js';

const config = loadConfig();
const app = createApp(config);
const { registry } = app.locals;

const server = app.listen(config.port, () => {
  log('INFO', `Gateway listening on http://localhost:${config.port}`);
  if (config.serviceDiscoveryUrl) {
    registry.start(3000);  // keep the list of instances up to date
    startRegistration(config.serviceDiscoveryUrl, config.selfUrl);
  } else {
    log('INFO', 'No SERVICE_DISCOVERY_URL: using the fixed *_SERVICE_URL addresses');
  }
});

// "docker stop" sends SIGTERM: deregister, finish the requests in progress, then exit.
process.on('SIGTERM', async () => {
  log('INFO', 'Shutting down');
  registry.stop();
  await stopRegistration(config.serviceDiscoveryUrl, config.selfUrl);
  server.close(() => process.exit(0));
});
