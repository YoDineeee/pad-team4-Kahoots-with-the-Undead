# PAD – In Kahoots with the Undead

A gateway, a service discovery and six services, in three languages:

| Service | Tech | Port (inside compose) | Gateway prefix | Storage | Instances |
|---|---|---|---|---|---|
| **gateway** | JavaScript, Node 22 + Express | **8080** (published) | – | – | 1 |
| **service-discovery** | JavaScript, Node 22 + Express | **8761** (published for the demo) | – | in memory | 1 |
| **player-service** | Java 21, Spring Boot | 8081 | `/api/players`, `/api/trades` | in memory | 1 |
| **game-service** | Java 21, Spring Boot | 8082 | `/api/game`, `/api/lobbies`, `/api/sessions` | in memory | 1 |
| **zombie-service** | Java 17, Spring Boot | 8081 | `/api/zombies` | PostgreSQL `zombie-db` | **3** (`ZOMBIE_REPLICAS`) |
| **resource-service** | Java 17, Spring Boot | 8082 | `/api/resources` | PostgreSQL `resource-db` | **3** (`RESOURCE_REPLICAS`) |
| **base-service** | Python 3.12, Flask | 5007 | `/api/bases` | in memory | 1 |
| **crafting-service** | Python 3.12, Flask | 5008 | `/api/crafting` | in memory | 1 |

Clients only ever call `http://localhost:8080`. In Docker Compose no service except the gateway (and
the Service Discovery, so you can look at it) publishes a port. Every service registers itself with the
Service Discovery, and the gateway asks the Service Discovery where the instances are. Every container
has its own address, so two services using 8081 inside the network don't clash.

`zombie-service` and `resource-service` come from `lab-services/`, `base-service` and
`crafting-service` from `lab1-grade2-repos/`. Each keeps its own README, Dockerfile and tests.

## Checklist

### Lab 1 (grade 5)

| Grade | Task | Where |
|---|---|---|
| 2 | Two HTTP CRUD services, in-memory storage | `player-service/`, `game-service/` |
| 3 | Script to build and run the project + README | `run.py`, this file, each service's `README.md` |
| 4 | Postman collection | `postman/PAD_Undead.postman_collection.json` |
| 5 | Dockerized, pushed to Docker Hub as `username/service:version` | `*/Dockerfile`, `python run.py push --user <you>` |

### Lab 2 (grade 5)

| Task | Where |
|---|---|
| New service – Gateway – the point of entry, in JavaScript | `gateway/` |
| Added to Docker Compose | `docker-compose.yml` (service `gateway`, only published port) |
| Pushed to Docker Hub | `python run.py push --user <you>` now pushes `<you>/gateway:<version>` too |
| All services behind the gateway | `gateway/src/config.js` (routing table), `docker-compose.yml` (6 services + 2 databases) |

### Lab 3 – Scaling up (grade 7)

| Grade | Task | Where |
|---|---|---|
| 2 | New private repo **Service Discovery**, submodule of the CPR, README, GitHub workflow | `service-discovery/` (`README.md`, `.github/workflows/ci.yml`, `.github/pull_request_template.md`) – see *Repository setup* below |
| 3 | Status endpoint in every service | `GET /status` everywhere: `*/observability/StatusController.java`, `*/observability.py`, `gateway/src/app.js`, `service-discovery/src/app.js` |
| 3 | Explicit logs in all services | one line per request in and out (status + duration) with the gateway's `X-Request-Id` (`RequestLoggingFilter.java`, `observability.py`, gateway `app.js`), one per business change (`Zombie type created ...`), one per rejected request (exception handlers / `error()`), every mocked call, registration |
| 4 | Service Discovery in our banned language (JavaScript), services register on startup | `service-discovery/src/registry.js`, `POST /register`; `ServiceRegistrar.java`, `observability.start_registration()`, `gateway/src/registrar.js` |
| 4 | Dockerized + Docker Hub | `service-discovery/Dockerfile`, `python run.py push --user <you>` (now 8 images) |
| 5 | 2–3 instances in the `.yaml`, dynamically added/removed | `docker-compose.yml` (`deploy.replicas: 3` for zombie and resource), heartbeats every 10 s + removal after 30 s of silence, the gateway refreshes its list every 3 s (`gateway/src/registry.js`); `python run.py scale zombie-service 2` |
| 6 | Round-robin load balancer in the gateway | `gateway/src/loadBalancer.js`, `X-Instance-Id` response header |
| 6 | Business logic made instance-agnostic | see *Which services scale* below (`ZombieType implements Persistable`, 409 on concurrent duplicates) |
| 7 | Circuit breaker: 3 errors in timeout × 3.5, all endpoints, trip is logged | `gateway/src/circuitBreaker.js` |

The circuit breaker lives in the gateway because it is the only component that calls other services
(the calls between game/base/crafting and other services are still mocks).

How to learn the code quickly: `docs/lab3-guide.md` (reading order, the 5 ideas, questions to
check yourself before the presentation).

#### Which services scale (instance-agnostic business logic)

- **zombie-service** and **resource-service** keep everything in PostgreSQL, so any instance can
  answer any request → **3 replicas each**. One real multi-instance bug was fixed: zombie types have
  client-chosen ids, so Spring Data's `save()` did a *merge*. Two instances handling the same
  `POST tourist_zombie` at the same moment both passed the `existsById()` check and the second one
  silently **overwrote** the first. `ZombieType` now implements `Persistable`, so a new type is
  INSERTed and the database's primary key rejects the duplicate → `409` from whichever instance lost
  (`ScalingSafetyTest`). Resource points get a database-generated UUID, so they were already safe.
- **player, game, base, crafting** keep their data **in memory** (a player registered on instance A
  does not exist on instance B), so they stay at **one instance**. Scaling them needs a shared
  database first (a later lab); `python run.py scale` works for them technically but would break
  their data.
- Every response says which instance answered (`X-Instance-Id`), and every log line carries the
  request id, so a request can be followed across the gateway and the instance that handled it.

#### Demo script (lab 3)

```bash
python run.py docker                                   # ~40 s until the Java instances registered
curl localhost:8761/services                           # grade 4-5: 3 zombie + 3 resource instances
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -D - localhost:8080/api/zombies/types | grep -i x-instance-id; done
                                                       # grade 6: the instance id rotates
python run.py scale zombie-service 2                   # grade 5: one stops, deregisters, gateway follows
docker kill pad-lab_2-resource-service-2               # grade 7: crash an instance, then
for i in $(seq 1 9); do curl -s -o /dev/null -w "%{http_code} " localhost:8080/api/resources/points; done
docker compose logs gateway | grep TRIPPED             #   CIRCUIT BREAKER TRIPPED for resource-service/...
curl localhost:8080/status                             #   circuit_breaker.trips
```

#### Repository setup (manual, on GitHub)

1. Create the **private** repository `service-discovery`, push the `service-discovery/` folder to it,
   add the professor and all colleagues as collaborators, protect `main` and `development`.
2. Add the repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` (for the CI workflow).
3. In the CPR: `git submodule add <url-of-service-discovery> service-discovery`.

In the real setup each service lives in its own **private** repository, linked as a submodule in the
Common Public Repository (CPR). The Postman collection and `docker-compose.yml` go into the CPR.

## Requirements

- **Docker Desktop** (the only thing you need for `python run.py docker`)
- **Python 3.8+** for the script
- Optional, for running without Docker: **JDK 21**, **Maven 3.9+** and **Node.js 22**

## Running

```bash
# 1. secrets: copy the example and change JWT_SECRET and the two DB passwords
#    (the script does the copy for you the first time)
cp .env.example .env

# 2. build all images and start the containers
python run.py docker

# 3. check everything is up (through the gateway) and registered (Service Discovery)
curl http://localhost:8761/services
curl http://localhost:8080/health
# {"status":"UP","gateway":"UP","services":{"player-service":"UP","game-service":"UP",
#   "zombie-service":"UP","resource-service":"UP","base-service":"UP","crafting-service":"UP"}}
# (the Java services need ~30 s after start; until then /health answers 503 DEGRADED)

# 4. call any service through the gateway
curl http://localhost:8080/api/zombies/types
curl http://localhost:8080/api/resources/points
curl http://localhost:8080/api/crafting/recipes -H "Authorization: Bearer player-token"

# stop
python run.py stop
```

Other commands:

```bash
python run.py local                                   # gateway + player + game without Docker
                                                      # (zombie/resource need PostgreSQL -> use docker)
python run.py test                                    # unit tests (4 Java services + gateway + 2 Python)
python run.py scale zombie-service 2                  # lab 3: change the number of instances live
python run.py push --user <dockerhub_user> --version 1.0.0
```

`push` builds each image, tags it as `<user>/<service>:<version>` (and `:latest`) and pushes it, for
all eight images (the Service Discovery too). `zombie-service` and `resource-service` keep their own version `1.1.0` (the one in
their `pom.xml`). Run `docker login` once before, and make the eight Docker Hub repositories **public**.

## Testing with Postman

1. Import `postman/PAD_Undead.postman_collection.json`.
2. Start the system (`python run.py docker`).
3. Open the collection → **Run** (Collection Runner) → run everything top to bottom.

All requests go to the gateway (`http://localhost:8080`). The first folder tests the gateway itself
(routing table, health of all six services, unknown route → 404). Then one folder per service:
Player, Game, Zombie, Resource, Base, Crafting. Every request saves the ids it creates (player ids,
JWTs, lobby id, session id ...) into collection variables for the next ones.

## How the services cooperate

- Every service **registers** with the **Service Discovery** on startup and sends a heartbeat every
  10 s; an instance silent for 30 s is removed from the registry.
- The **gateway** forwards by path prefix (see the table at the top) to an instance chosen **round
  robin** among the ones the Service Discovery lists, adds an `X-Request-Id`, and answers 502/504
  itself if an instance is down or too slow. 3 such failures of one instance within
  `UPSTREAM_TIMEOUT_MS * 3.5` trip the **circuit breaker**, which logs an ERROR.
  `GET /health` asks every service at once:
  Spring Boot services on `/actuator/health` (for zombie/resource that also checks the database),
  Flask services on `/health`.
- The Player Service **issues** JWTs on login. The Game Service only **validates** them.
  Both read the same `JWT_SECRET`, so the Game Service doesn't have to call the Player Service on
  every request. The gateway passes the `Authorization` header through untouched.
- Calls between services (Game → Player/Resource, Base → Resource/World, Crafting → Resource/Base ...)
  still go through small **mock** clients (`game-service/.../client/`, `base-service/clients.py`,
  `crafting-service/clients.py`). They now all run in the same network, so replacing a mock with a
  real HTTP call only needs the target's service-name URL.
- Base and Crafting accept demo tokens (`service-token`, `moderator-token`, anything else = player),
  not the JWTs from the Player Service yet. The gateway passes the header through either way.

## Scope

Not yet implemented: trades, zombie actions, advancing the cycle manually and WebSockets – planned
for the next labs.

## Secrets

`.env` is in `.gitignore` and must never be committed (nor `node_modules`). Only `.env.example`
(with fake values) is in git. The default secret inside `application.properties` exists only so
`mvn spring-boot:run` works on a developer laptop.

