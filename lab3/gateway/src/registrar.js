// ============================================================================
// SELF-REGISTRATION: the gateway registers itself with the Service Discovery like every other
// service: on startup, again every 10 s (heartbeat), and it deregisters on shutdown.
// ============================================================================

import os from 'node:os';
import { log } from './log.js';

const INSTANCE_ID = `gateway-${os.hostname()}`; // in Docker the hostname is the container id

async function call(method, url, body) {
  return fetch(url, {
    method,
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(2000),
  });
}

export function startRegistration(discoveryUrl, selfUrl) {
  if (!discoveryUrl || !selfUrl) return;

  async function register() {
    try {
      const response = await call('POST', `${discoveryUrl}/register`, { service: 'gateway', instance_id: INSTANCE_ID, url: selfUrl });
      if (response.status === 201) log('INFO', `Registered with the Service Discovery as ${INSTANCE_ID}`);
    } catch (error) {
      log('WARN', `Could not reach the Service Discovery (${error.message}), retrying in 10 s`);
    }
  }

  register();
  setInterval(register, 10000);
}

export async function stopRegistration(discoveryUrl, selfUrl) {
  if (!discoveryUrl || !selfUrl) return;
  try {
    await call('DELETE', `${discoveryUrl}/services/gateway/instances/${INSTANCE_ID}`, {});
    log('INFO', 'Deregistered from the Service Discovery');
  } catch (error) {
    log('WARN', `Could not deregister (${error.message})`);
  }
}
