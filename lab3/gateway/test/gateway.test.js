// Run with: npm test   (uses Node's built-in test runner, no extra libraries)
//
// Instead of the real Java services we start tiny fake HTTP servers ("stubs")
// that echo back what they received. Then we check what the gateway forwarded.

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';

process.env.QUIET_LOGS = '1';

function listen(server) {
  return new Promise((resolve) => server.listen(0, () => resolve(`http://127.0.0.1:${server.address().port}`)));
}

/** A fake service that replies with a JSON echo of the request it received. */
function createEchoService(name) {
  return http.createServer((req, res) => {
    if (req.url === '/actuator/health' || req.url === '/health') {
      res.writeHead(200, { 'content-type': 'application/json' }).end('{"status":"UP"}');
      return;
    }
    if (req.url.startsWith('/api/game/slow')) {
      setTimeout(() => res.end('too late'), 1_000);
      return;
    }
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      const status = req.headers.authorization === 'Bearer bad' ? 401 : 201;
      res.writeHead(status, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ service: name, method: req.method, url: req.url, headers: req.headers, body }));
    });
  });
}

const EXTRA_SERVICES = ['zombie', 'resource', 'base', 'crafting'];
let playerStub, gameStub, extraStubs, gateway, gatewayUrl, deadServiceUrl;

before(async () => {
  playerStub = createEchoService('player');
  gameStub = createEchoService('game');
  const playerUrl = await listen(playerStub);
  const gameUrl = await listen(gameStub);

  // zombie, resource, base and crafting stubs -> { zombieServiceUrl: 'http://...', ... }
  extraStubs = EXTRA_SERVICES.map((name) => createEchoService(name));
  const extraUrls = {};
  for (const [i, name] of EXTRA_SERVICES.entries()) {
    extraUrls[`${name}ServiceUrl`] = await listen(extraStubs[i]);
  }

  // An address where nothing is listening, to simulate a crashed service.
  const tmp = http.createServer();
  deadServiceUrl = await listen(tmp);
  await new Promise((r) => tmp.close(r));

  const app = createApp({ playerServiceUrl: playerUrl, gameServiceUrl: gameUrl, ...extraUrls, timeoutMs: 300 });
  gateway = http.createServer(app);
  gatewayUrl = await listen(gateway);
});

after(() => {
  for (const server of [playerStub, gameStub, ...extraStubs, gateway]) server.close();
});

test('forwards /api/players/* to the Player Service with method, path, query and body', async () => {
  const response = await fetch(`${gatewayUrl}/api/players/register?source=test`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: 'kiki' }),
  });
  const echo = await response.json();

  assert.equal(response.status, 201);
  assert.equal(echo.service, 'player');
  assert.equal(echo.method, 'POST');
  assert.equal(echo.url, '/api/players/register?source=test');
  assert.deepEqual(JSON.parse(echo.body), { username: 'kiki' });
  assert.equal(response.headers.get('x-served-by'), 'player-service');
});

test('forwards lobbies, sessions and game routes to the Game Service', async () => {
  for (const path of ['/api/game/lobbies', '/api/lobbies/l-1/start', '/api/sessions/s-1']) {
    const echo = await (await fetch(gatewayUrl + path, { method: 'POST' })).json();
    assert.equal(echo.service, 'game', path);
    assert.equal(echo.url, path);
  }
});

test('forwards zombies, resources, bases and crafting to their own services', async () => {
  const cases = {
    '/api/zombies/types': 'zombie',
    '/api/resources/points?room_id=r-1': 'resource',
    '/api/bases/b-1/rooms': 'base',
    '/api/crafting/recipes': 'crafting',
  };
  for (const [path, service] of Object.entries(cases)) {
    const response = await fetch(gatewayUrl + path, { headers: { authorization: 'Bearer player-token' } });
    const echo = await response.json();
    assert.equal(echo.service, service, path);
    assert.equal(echo.url, path);
    assert.equal(echo.headers.authorization, 'Bearer player-token');
    assert.equal(response.headers.get('x-served-by'), `${service}-service`);
  }
});

test('passes the Authorization header through and returns the service status unchanged', async () => {
  const response = await fetch(`${gatewayUrl}/api/players/p-1`, { headers: { authorization: 'Bearer bad' } });
  const echo = await response.json();
  assert.equal(response.status, 401);
  assert.equal(echo.headers.authorization, 'Bearer bad');
});

test('adds a request id and forwarding headers', async () => {
  const response = await fetch(`${gatewayUrl}/api/players/p-1`, { headers: { 'x-request-id': 'abc-123' } });
  const echo = await response.json();
  assert.equal(response.headers.get('x-request-id'), 'abc-123');
  assert.equal(echo.headers['x-request-id'], 'abc-123');
  assert.ok(echo.headers['x-forwarded-for']);
});

test('unknown route -> 404 ROUTE_NOT_FOUND', async () => {
  const response = await fetch(`${gatewayUrl}/api/unknown`);
  assert.equal(response.status, 404);
  assert.equal((await response.json()).error, 'ROUTE_NOT_FOUND');
});

test('slow service -> 504 GATEWAY_TIMEOUT', async () => {
  const response = await fetch(`${gatewayUrl}/api/game/slow`);
  assert.equal(response.status, 504);
  assert.equal((await response.json()).error, 'GATEWAY_TIMEOUT');
});

test('service down -> 502 SERVICE_UNAVAILABLE', async () => {
  const app = createApp({ playerServiceUrl: deadServiceUrl, gameServiceUrl: deadServiceUrl, timeoutMs: 300 });
  const server = http.createServer(app);
  const url = await listen(server);
  try {
    const response = await fetch(`${url}/api/players/p-1`);
    assert.equal(response.status, 502);
    assert.equal((await response.json()).error, 'SERVICE_UNAVAILABLE');
  } finally {
    server.close();
  }
});

test('GET /health is 200 when every service is UP', async () => {
  const response = await fetch(`${gatewayUrl}/health`);
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.deepEqual(body.services, {
    'player-service': 'UP',
    'game-service': 'UP',
    'zombie-service': 'UP',
    'resource-service': 'UP',
    'base-service': 'UP',
    'crafting-service': 'UP',
  });
});

test('GET /health is 503 when a service is DOWN', async () => {
  const app = createApp({ playerServiceUrl: deadServiceUrl, gameServiceUrl: deadServiceUrl, timeoutMs: 300 });
  const server = http.createServer(app);
  const url = await listen(server);
  try {
    const response = await fetch(`${url}/health`);
    assert.equal(response.status, 503);
    assert.equal((await response.json()).services['player-service'], 'DOWN');
  } finally {
    server.close();
  }
});
