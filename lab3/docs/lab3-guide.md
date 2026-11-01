# Lab 3 in one hour – a learning guide

Goal of the lab: more users → run **several copies (instances)** of a service, and make the rest of
the system cope with that. Everything we added serves one of five ideas. Learn the ideas first, then
read the code that implements each one; every piece is small (the biggest file is under 100 lines).

## The five ideas

### 1. A service can have many instances, and each needs its own name and address

`zombie-service` is the *service*. `docker compose` starts 3 *instances* of it: three containers,
each with its own IP address. The code that runs is identical; only the identity differs.

- Java: `observability/InstanceInfo.java` – id = `"<service>-<hostname>"` (in Docker the hostname is
  the container id, so every replica gets a different one), address = the container's IP.
- Python: `instance_id()` in `observability.py` does the same.
- Every answer carries the header `X-Instance-Id`, so you can *see* which instance answered.

### 2. Instances announce themselves: registration + heartbeat

Nobody configures "zombie-service has 3 instances at these IPs". Each instance tells the
**Service Discovery** itself:

- on startup: `POST /register {service, instance_id, url}`
- every 10 s: the same POST again – the **heartbeat** ("still alive")
- on a clean shutdown: `DELETE /services/{service}/instances/{id}`
- if it crashes, the heartbeats stop and the Service Discovery removes it after 30 s

Code: `service-discovery/src/registry.js` (the registry, 4 methods), `ServiceRegistrar.java`
(`sendHeartbeat()` runs every 10 s thanks to `@Scheduled`, `deregister()` runs on shutdown thanks to
`@PreDestroy`), `start_registration()` in `observability.py` (a thread with a `while True` loop).

Why a heartbeat and not just "register once"? Because a crashed process can't say goodbye. Silence
for 30 s (three missed heartbeats) is how we notice it.

### 3. The gateway asks where the instances are

Every 3 s `gateway/src/registry.js` downloads `GET /services` and keeps the list in memory. New
instances get requests a few seconds after they start; removed ones stop getting them. This is what
"dynamically added/removed" (grade 5) means: `python run.py scale zombie-service 2` changes the
number while everything keeps running.

### 4. Round robin spreads the requests

`gateway/src/loadBalancer.js`, 20 lines: one counter per service, the next instance is
`instances[counter % instances.length]`. With A, B, C: A, B, C, A, B, C ...

**Instance-agnostic** (grade 6) means: any instance must be able to answer any request.

- zombie and resource keep their data in PostgreSQL, shared by all instances → safe to scale.
- One real bug appeared with several instances: two instances creating the same zombie type at the
  same moment both passed the "does it exist?" check, and the second **overwrote** the first. Fix:
  `ZombieType implements Persistable`, so Spring INSERTs new types and the database rejects the
  duplicate → 409. Test: `ScalingSafetyTest`.
- player, game, base, crafting keep data **in memory**: a player created on instance A doesn't exist
  on instance B. So they stay at 1 instance (they'd need a shared database first).

### 5. The circuit breaker notices a failing instance

`gateway/src/circuitBreaker.js`, 40 lines. Rule from the lab: **3 errors of one instance within
(task timeout × 3.5)**, counting all its endpoints together. With `UPSTREAM_TIMEOUT_MS=10000` the
window is 35 s.

- an error = unreachable (502), too slow (504) or the instance answered 5xx
- a 4xx is NOT an error: "not found" means the service works and the request was wrong
- each failure is stored with its time; old ones (outside the window) are dropped; at 3 → TRIP,
  logged as `ERROR ... CIRCUIT BREAKER TRIPPED for zombie-service/zombie-2`

At grade 7 the trip is only **logged**. (Grade 10 would also remove the instance.)

## Follow one request

`GET http://localhost:8080/api/zombies/types`

1. Gateway `app.js`: gives it a request id, e.g. `9f1c...`.
2. Gateway `proxy.js`: `registry.instancesOf("zombie-service")` → 3 instances; `balancer.pick()` → zombie-2.
3. Gateway forwards to `http://172.18.0.8:8081/api/zombies/types` with header `X-Request-Id: 9f1c...`.
4. zombie-2 `RequestLoggingFilter`: puts `9f1c...` in the MDC and logs `--> GET /api/zombies/types`;
   the controller runs; it logs `<-- GET /api/zombies/types 200 (12 ms)`. Both lines show `[9f1c...]`.
5. Gateway copies the answer back, adds `X-Instance-Id: zombie-2`, logs `GET ... -> 200 by zombie-2 [request 9f1c...]`.
6. If step 3 had failed (502/504/5xx), `breaker.recordFailure("zombie-service", "zombie-2", ...)`.

## Reading order (about an hour)

| # | File | Time | What to look for |
|---|---|---|---|
| 1 | `service-discovery/src/registry.js` | 10 min | `register`, `deregister`, `removeSilentInstances` |
| 2 | `service-discovery/src/app.js` | 5 min | the 5 endpoints |
| 3 | `zombie-service/.../observability/*.java` (4 files) | 15 min | identity, request logs, `/status`, heartbeat |
| 4 | `base-service/observability.py` | 5 min | the same 4 things in Python |
| 5 | `gateway/src/registry.js` | 5 min | `refresh()` |
| 6 | `gateway/src/loadBalancer.js` | 2 min | `pick()` |
| 7 | `gateway/src/proxy.js` | 5 min | steps 1–5, where `recordFailure` is called |
| 8 | `gateway/src/circuitBreaker.js` | 5 min | the window filter |
| 9 | `docker-compose.yml` | 5 min | `deploy.replicas`, `SERVICE_DISCOVERY_URL` |
| 10 | `gateway/test/scaling.test.js` | 5 min | each test = one behaviour you can demo |

## Try it yourself

```bash
python run.py docker
curl localhost:8761/services                                  # who is registered
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -D - localhost:8080/api/zombies/types | grep -i x-instance-id; done
python run.py scale zombie-service 2                          # watch "Deregistered" in the logs
docker compose logs -f service-discovery gateway              # (in a second terminal)
docker kill pad-lab_2-zombie-service-1
for i in $(seq 1 9); do curl -s -o /dev/null -w "%{http_code} " localhost:8080/api/zombies/types; done
docker compose logs gateway | grep TRIPPED
```

## Check yourself

<details><summary>Why does every instance need a different id?</summary>
Because the registry stores instances by id. Two instances with the same id would overwrite each
other's entry and the gateway would only know one of them.
</details>

<details><summary>What is a heartbeat, and what happens when it stops?</summary>
The same registration call repeated every 10 s. If the Service Discovery hears nothing from an
instance for 30 s, it assumes a crash and removes it; the gateway stops using it at its next
refresh (≤ 3 s later).
</details>

<details><summary>Why don't player/game/base/crafting run several instances?</summary>
Their data is in memory, so each instance would have different data. A request routed to the "wrong"
instance would not find the player/lobby/base. They need a shared database first.
</details>

<details><summary>Which bug did scaling zombie-service reveal, and how was it fixed?</summary>
With client-chosen ids, Spring Data's save() merges (insert or update). Two instances creating the
same type at once both passed existsById() and the second overwrote the first. Implementing
Persistable makes Spring INSERT new entities, so the database's primary key rejects the second
→ 409.
</details>

<details><summary>Why is a 404 not counted by the circuit breaker, but a 500 is?</summary>
A 404 is a correct answer from a healthy service (the thing doesn't exist). A 500, a timeout or a
refused connection means the instance itself has a problem.
</details>

<details><summary>Why 3 errors "within timeout × 3.5" and not just "3 errors"?</summary>
Without a time window, 3 random errors over a whole day would trip it. The window makes it react only
to errors that come close together, i.e. a real problem.
</details>

<details><summary>Where does the circuit breaker live, and why there?</summary>
In the gateway, because it's the only component that calls other services over the network (the
calls between game/base/crafting and other services are still mocks).
</details>

<details><summary>What would grades 8–10 add?</summary>
8: the Service Discovery polls every instance's /status and logs alerts when the load is critical.
9: those alerts are sent to Discord/Telegram/email. 10: the circuit breaker removes the failing
instance instead of only logging.
</details>


