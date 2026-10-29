# Gateway

The single point of entry of the system. Clients (Postman, a frontend) talk **only** to the gateway on
port **8080**; it forwards each request to the right microservice and streams the answer back.
Node.js 22 + Express 5, written in JavaScript.

## Why a gateway?

- **One address for clients.** They don't need to know that players live on 8081 and games on 8082,
  or that services move, split or scale.
- **Services stay private.** In Docker Compose only the gateway publishes a port; the Java services
  are reachable only inside the compose network.
- **One place for cross-cutting concerns:** request ids, logging, timeouts, health, uniform errors,
  and since lab 3 **load balancing** and a **circuit breaker**.

## Run

```bash
npm install
npm start            # http://localhost:8080
npm test             # 20 tests, Node's built-in runner, uses fake services (no Java needed)

# Docker
docker build -t gateway .
docker run -p 8080:8080 -e PLAYER_SERVICE_URL=http://host.docker.internal:8081 \
  -e GAME_SERVICE_URL=http://host.docker.internal:8082 gateway
```

| Env variable | Default | Meaning |
|---|---|---|
| `PORT` | 8080 | port the gateway listens on |
| `PLAYER_SERVICE_URL` | http://localhost:8081 | where the Player Service lives (`http://player-service:8081` in compose) |
| `GAME_SERVICE_URL` | http://localhost:8082 | where the Game Service lives (`http://game-service:8082` in compose) |
| `ZOMBIE_SERVICE_URL` | http://localhost:8083 | Zombie Service (`http://zombie-service:8081` in compose) |
| `RESOURCE_SERVICE_URL` | http://localhost:8084 | Resource Service (`http://resource-service:8082` in compose) |
| `BASE_SERVICE_URL` | http://localhost:5007 | Base Service (`http://base-service:5007` in compose) |
| `CRAFTING_SERVICE_URL` | http://localhost:5008 | Crafting Service (`http://crafting-service:5008` in compose) |
| `UPSTREAM_TIMEOUT_MS` | 10000 | after this, the gateway answers 504 (the "task timeout limit") |
| `SERVICE_DISCOVERY_URL` | – | lab 3: where the Service Discovery lives (`http://service-discovery:8761` in compose). When set, the `*_SERVICE_URL` variables are ignored |
| `SELF_URL` | – | the gateway's own address, so it can register itself too (`http://gateway:8080`)

## Routing table (`src/config.js`)

| Path prefix | Service |
|---|---|
| `/api/players/**`, `/api/trades/**` | player-service |
| `/api/game/**`, `/api/lobbies/**`, `/api/sessions/**` | game-service |
| `/api/zombies/**` | zombie-service |
| `/api/resources/**` | resource-service |
| `/api/bases/**` | base-service |
| `/api/crafting/**` | crafting-service |

The path, query string, method, headers (including `Authorization`) and body are forwarded unchanged,
so every endpoint from the service READMEs works through the gateway, e.g.
`POST http://localhost:8080/api/players/login`.

## Gateway's own endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/` | name + routing table |
| GET | `/health` | `200` if all services are UP, `503` otherwise: `{"status":"UP","gateway":"UP","services":{"player-service":"UP", ...},"instances":{"zombie-service":[{"instance_id":"...","status":"UP"}, ...]}}`. Every instance is asked; a service is UP if at least one instance is. Spring Boot services are asked on `/actuator/health`, Flask services on `/health` (`healthPath` in the routing table) |
| GET | `/status` | lab 3: `requests_total`, the instances it balances over, the round-robin counters and the circuit breaker (`max_failures`, `window_ms`, `trips`) |

## Errors produced by the gateway

Same shape as the services: `{ "error": "CODE", "message": "..." }`.

| Status | `error` | When |
|---|---|---|
| 404 | `ROUTE_NOT_FOUND` | no service handles that path |
| 503 | `NO_INSTANCE_AVAILABLE` | no instance of that service is registered |
| 502 | `SERVICE_UNAVAILABLE` | the service is down / unreachable |
| 504 | `GATEWAY_TIMEOUT` | the service did not answer within `UPSTREAM_TIMEOUT_MS` |

Errors from a service (401, 404, 409 ...) are passed through untouched.

## Lab 3: scaling up

**Where are the services? (grade 5)** With `SERVICE_DISCOVERY_URL` set, `src/registry.js` downloads
`GET /services` from the Service Discovery every 3 s. An instance started with
`docker compose up -d --scale zombie-service=4 --no-recreate` registers itself and gets requests a
few seconds later; a stopped one disappears the same way (log lines `Instance joined` /
`Instance left`).

**Round robin (grade 6).** `src/loadBalancer.js` keeps one counter per service: with instances
`[A, B, C]` requests go A, B, C, A, B, C ... (`counter % number of instances`). Every answer carries
`X-Instance-Id`, so you can see it in Postman or with `curl -i`.

**Circuit breaker (grade 7).** `src/circuitBreaker.js` counts failures **per instance, over all its
endpoints**: unreachable (502), timeout (504) or a 5xx answer. 4xx answers are not failures (the
service works, the request was wrong). **3 failures within `UPSTREAM_TIMEOUT_MS * 3.5`** (35 s by
default) trip it, and the trip is logged:

```
ERROR [gateway] CIRCUIT BREAKER TRIPPED for zombie-service/zombie-2: 3 failures within 35000 ms
```

The gateway is the only component that calls other services (the calls between game/base/crafting
and other services are still mocks), so this is where the breaker lives.

**Try it** (with `python run.py docker` running):

```bash
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -D - localhost:8080/api/zombies/types | grep -i x-instance-id; done
docker kill pad-lab_2-zombie-service-2                      # crash one instance
for i in 1 2 3 4 5 6 7 8 9; do curl -s -o /dev/null -w "%{http_code} " localhost:8080/api/zombies/types; done
docker compose logs gateway | grep TRIPPED                  # every 3rd request was a 502
curl localhost:8080/status                                  # circuit_breaker.trips
```

About 30 s later the Service Discovery stops listing the dead instance (no heartbeats), and the
gateway stops sending it requests.

## Code tour

Read them in this order (about 500 lines in total):

1. `src/server.js` – starts the gateway (`npm start`), the registry refresh and the self-registration.
2. `src/config.js` – environment variables and the **routing table** (prefix → service).
3. `src/app.js` – the Express app: raw body → request id + log → `GET /`, `GET /health`, `GET /status` → one proxy per route → 404.
4. `src/proxy.js` – picks an instance (round robin), forwards one request with the built-in `fetch()`,
   copies the answer back. No instance → 503, instance down → 502, too slow → 504; failures go to the circuit breaker.
5. `src/registry.js` – which instances exist (from the Service Discovery, or fixed URLs without one).
6. `src/loadBalancer.js` – round robin (20 lines).
7. `src/circuitBreaker.js` – 3 failures in the window → log (40 lines).
8. `src/health.js` – asks every instance's health path in parallel.
9. `src/registrar.js` – registers the gateway itself with the Service Discovery.
10. `src/log.js` – one log line with time, level and `[gateway]`.

`test/gateway.test.js` (10 tests) checks the forwarding with fixed URLs; `test/scaling.test.js`
(10 tests) uses a fake Service Discovery and three fake instances for round robin, instances
joining/leaving and the circuit breaker.

Response headers added: `X-Request-Id` (follow one request through all logs), `X-Served-By`
(which service answered) and `X-Instance-Id` (which instance of it).

## Contributing

- Branches: `main` (releases) and `development` (integration), both protected; work on
  `feature/<issue>-short-name` or `fix/<issue>-short-name` and open a PR into `development`.
- Every PR uses `.github/pull_request_template.md` and must pass the **CI** workflow
  (`.github/workflows/ci.yml`: `npm test` + Docker build) and get the agreed number of approvals.
- Releasing: tag `vX.Y.Z` on `main` → CI pushes `<user>/gateway:X.Y.Z` and `:latest` to Docker Hub
  (needs the `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets).

## Design choice: simple over clever

The proxy reads the whole request body, then calls the service with `fetch()`, then reads the whole
answer and sends it back. That's easy to read and debug, and fine for small JSON requests like ours
(body limit 1 MB). A production gateway would **stream** the bytes instead of holding them in memory
(e.g. with the `http-proxy-middleware` library), but the behaviour seen by the client is the same.
