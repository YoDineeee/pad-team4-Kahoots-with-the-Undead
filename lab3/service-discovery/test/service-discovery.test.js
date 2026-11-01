// Run with: npm test   (Node's built-in test runner, no extra libraries)

process.env.QUIET_LOGS = '1';

import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';
import { Registry } from '../src/registry.js';

let clock, registry, server, url;

beforeEach(async () => {
  clock = 1_000_000; // a fake clock we can move forward
  registry = new Registry(() => clock);
  server = http.createServer(createApp(registry));
  await new Promise((resolve) => server.listen(0, resolve));
  url = `http://127.0.0.1:${server.address().port}`;
});

afterEach(() => server.close());

function register(body) {
  return fetch(`${url}/register`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

const zombie1 = { service: 'zombie-service', instance_id: 'zombie-1', url: 'http://10.0.0.1:8081' };
const zombie2 = { service: 'zombie-service', instance_id: 'zombie-2', url: 'http://10.0.0.2:8081' };

test('first registration -> 201, the same instance again (heartbeat) -> 200', async () => {
  assert.equal((await register(zombie1)).status, 201);
  assert.equal((await register(zombie1)).status, 200);
});

test('GET /services lists every instance of every service', async () => {
  await register(zombie1);
  await register(zombie2);
  await register({ service: 'base-service', instance_id: 'base-1', url: 'http://10.0.0.3:5007' });

  const services = await (await fetch(`${url}/services`)).json();
  assert.deepEqual(services['zombie-service'].map((i) => i.instance_id), ['zombie-1', 'zombie-2']);
  assert.equal(services['base-service'][0].url, 'http://10.0.0.3:5007');
});

test('a registration without service, instance_id or url -> 400', async () => {
  assert.equal((await register({ service: 'zombie-service' })).status, 400);
});

test('deregister on shutdown removes the instance', async () => {
  await register(zombie1);
  const response = await fetch(`${url}/services/zombie-service/instances/zombie-1`, { method: 'DELETE' });
  assert.equal(response.status, 200);
  assert.deepEqual(await (await fetch(`${url}/services`)).json(), {});

  const again = await fetch(`${url}/services/zombie-service/instances/zombie-1`, { method: 'DELETE' });
  assert.equal(again.status, 404);
});

test('an instance that stops sending heartbeats is removed after the TTL', async () => {
  await register(zombie1);
  await register(zombie2);

  clock += 20_000;
  await register(zombie2); // zombie-2 keeps sending heartbeats, zombie-1 crashed
  clock += 15_000;         // zombie-1 is now silent for 35 s

  registry.removeSilentInstances(30_000);
  const services = await (await fetch(`${url}/services`)).json();
  assert.deepEqual(services['zombie-service'].map((i) => i.instance_id), ['zombie-2']);
});

test('GET /status', async () => {
  await register(zombie1);
  const status = await (await fetch(`${url}/status`)).json();
  assert.equal(status.status, 'UP');
  assert.equal(status.registered_instances, 1);
});
