// Lab 3: instances from the Service Discovery, round-robin load balancing, circuit breaker.
//
// We start a fake Service Discovery (we decide what GET /services answers) and three fake
// instances of zombie-service that say who they are.

process.env.QUIET_LOGS = '1';

import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';
import { CircuitBreaker } from '../src/circuitBreaker.js';

async function listen(server) {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${server.address().port}`;
}

/** A fake instance. Set instance.mode to 'ok', 'error500', 'notfound' or 'slow'. */
async function startInstance(id) {
  const instance = { id, mode: 'ok' };
  instance.server = http.createServer((req, res) => {
    if (instance.mode === 'slow') return setTimeout(() => res.end('too late'), 500);
    const status = { ok: 200, error500: 500, notfound: 404 }[instance.mode];
    res.writeHead(status, { 'content-type': 'application/json' }).end(JSON.stringify({ served_by: id }));
  });
  instance.url = await listen(instance.server);
  return instance;
}

let discovery, discoveryAnswer, instances, gateway, gatewayUrl, app;

beforeEach(async () => {
  instances = await Promise.all(['zombie-1', 'zombie-2', 'zombie-3'].map(startInstance));
  discoveryAnswer = { 'zombie-service': instances.map((i) => ({ instance_id: i.id, url: i.url })) };
  discovery = http.createServer((req, res) => res.end(JSON.stringify(discoveryAnswer)));
  const discoveryUrl = await listen(discovery);

  app = createApp({ serviceDiscoveryUrl: discoveryUrl, timeoutMs: 200 });
  await app.locals.registry.refresh(); // what the gateway does every 3 s
  gateway = http.createServer(app);
  gatewayUrl = await listen(gateway);
});

afterEach(() => {
  for (const server of [discovery, gateway, ...instances.map((i) => i.server)]) server.close();
});

async function get(path) {
  const response = await fetch(gatewayUrl + path);
  const body = await response.json().catch(() => null);
  return { status: response.status, instance: response.headers.get('x-instance-id'), body };
}

async function gatewayStatus() {
  return (await fetch(`${gatewayUrl}/status`)).json();
}

test('round robin: zombie-1, zombie-2, zombie-3, zombie-1, zombie-2, zombie-3', async () => {
  const servedBy = [];
  for (let i = 0; i < 6; i++) servedBy.push((await get('/api/zombies/types')).body.served_by);
  assert.deepEqual(servedBy, ['zombie-1', 'zombie-2', 'zombie-3', 'zombie-1', 'zombie-2', 'zombie-3']);
});

test('the X-Instance-Id header says which instance answered', async () => {
  const response = await get('/api/zombies/types');
  assert.equal(response.instance, response.body.served_by);
});

test('instances that leave or join the Service Discovery are picked up on the next refresh', async () => {
  discoveryAnswer = { 'zombie-service': [{ instance_id: 'zombie-2', url: instances[1].url }] };
  await app.locals.registry.refresh();
  for (let i = 0; i < 3; i++) assert.equal((await get('/api/zombies/types')).body.served_by, 'zombie-2');

  discoveryAnswer = { 'zombie-service': instances.map((i) => ({ instance_id: i.id, url: i.url })) };
  await app.locals.registry.refresh();
  const servedBy = new Set();
  for (let i = 0; i < 3; i++) servedBy.add((await get('/api/zombies/types')).body.served_by);
  assert.equal(servedBy.size, 3);
});

test('no instance of a service -> 503 NO_INSTANCE_AVAILABLE', async () => {
  const response = await get('/api/players/p-1');
  assert.equal(response.status, 503);
  assert.equal(response.body.error, 'NO_INSTANCE_AVAILABLE');
});

test('circuit breaker: 3 errors of the same instance trip it (and it is logged)', async () => {
  instances[1].mode = 'error500'; // zombie-2 is broken
  for (let i = 0; i < 9; i++) await get('/api/zombies/types'); // zombie-2 gets requests 2, 5 and 8

  const { circuit_breaker } = await gatewayStatus();
  assert.equal(circuit_breaker.trips.length, 1);
  assert.equal(circuit_breaker.trips[0].instance, 'zombie-service/zombie-2');
  assert.equal(circuit_breaker.window_ms, 200 * 3.5);
});

test('circuit breaker: timeouts count too, over all endpoints of the instance', async () => {
  discoveryAnswer = { 'zombie-service': [{ instance_id: 'zombie-1', url: instances[0].url }] };
  await app.locals.registry.refresh();
  instances[0].mode = 'slow';

  assert.equal((await get('/api/zombies/types')).status, 504);
  assert.equal((await get('/api/zombies/types/a')).status, 504);
  assert.equal((await get('/api/zombies/types/b')).status, 504);
  assert.equal((await gatewayStatus()).circuit_breaker.trips.length, 1);
});

test('circuit breaker: a dead instance (connection refused) counts too', async () => {
  discoveryAnswer = { 'zombie-service': [{ instance_id: 'zombie-1', url: instances[0].url }] };
  await app.locals.registry.refresh();
  await new Promise((resolve) => instances[0].server.close(resolve)); // zombie-1 crashes

  for (let i = 0; i < 3; i++) assert.equal((await get('/api/zombies/types')).status, 502);
  assert.equal((await gatewayStatus()).circuit_breaker.trips.length, 1);
});

test('circuit breaker: 4xx answers are NOT failures', async () => {
  instances.forEach((i) => (i.mode = 'notfound'));
  for (let i = 0; i < 9; i++) assert.equal((await get('/api/zombies/types/x')).status, 404);
  assert.equal((await gatewayStatus()).circuit_breaker.trips.length, 0);
});

test('circuit breaker (grade 7): after the trip the instance still gets requests', async () => {
  discoveryAnswer = { 'zombie-service': [{ instance_id: 'zombie-1', url: instances[0].url }] };
  await app.locals.registry.refresh();
  instances[0].mode = 'error500';
  for (let i = 0; i < 3; i++) await get('/api/zombies/types');

  instances[0].mode = 'ok';
  assert.equal((await get('/api/zombies/types')).body.served_by, 'zombie-1');
});

test('circuit breaker: failures older than the window (timeout * 3.5) do not count', () => {
  let clock = 0;
  const breaker = new CircuitBreaker(1000, () => clock); // window = 3500 ms

  breaker.recordFailure('zombie-service', 'zombie-1', 'test'); // t = 0
  clock = 2000;
  breaker.recordFailure('zombie-service', 'zombie-1', 'test'); // t = 2000
  clock = 4000;
  breaker.recordFailure('zombie-service', 'zombie-1', 'test'); // t = 4000: the first one is too old
  assert.equal(breaker.trips.length, 0);

  clock = 5000;
  breaker.recordFailure('zombie-service', 'zombie-1', 'test'); // 2000, 4000, 5000 -> 3 in 3500 ms
  assert.equal(breaker.trips.length, 1);
});
