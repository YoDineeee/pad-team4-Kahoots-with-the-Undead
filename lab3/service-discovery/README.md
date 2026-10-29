# Service Discovery

A phone book of the running services: every instance tells it "I'm `zombie-service`, instance
`zombie-1`, you can reach me at `http://172.18.0.7:8081`", and the gateway asks it "where are the
instances of `zombie-service`?". Node.js 22 + Express 5, written in JavaScript like the gateway.
Port **8761**.

## Run

```bash
npm install
npm start            # http://localhost:8761
npm test             # 6 tests, Node's built-in runner

docker build -t service-discovery .
docker run -p 8761:8761 service-discovery
```

In the project it is started by `docker-compose.yml` together with everything else.

## API

| Method | Path | Body / answer |
|---|---|---|
| POST | `/register` | `{"service": "zombie-service", "instance_id": "zombie-1", "url": "http://172.18.0.7:8081"}` → **201** the first time, **200** when it is a heartbeat, **400** if a field is missing |
| DELETE | `/services/{service}/instances/{instance_id}` | deregister → **200**, or **404** if it wasn't registered |
| GET | `/services` | `{"zombie-service": [{"instance_id": "zombie-1", "url": "...", "last_heartbeat": "..."}, ...], ...}` |
| GET | `/status` | `{"service": "service-discovery", "status": "UP", "uptime_seconds": 120, "registered_instances": 11}` |
| GET | `/health` | `{"status": "UP"}` (for Docker) |

## How an instance comes and goes

1. **Start:** the service calls `POST /register` → it is in the registry.
2. **Running:** it calls `POST /register` again every 10 s. That repeated call is the **heartbeat**:
   it only refreshes `last_heartbeat`.
3. **Clean stop** (`docker stop`, `--scale` down): the service calls `DELETE …` → removed at once.
4. **Crash** (`docker kill`, out of memory): no DELETE and no more heartbeats. Every 5 s the
   Service Discovery removes the instances that have been silent for more than `INSTANCE_TTL_MS`
   (30 s = three missed heartbeats).

If the Service Discovery itself restarts, its memory is empty, but within 10 s every service has
sent its next heartbeat, so the registry rebuilds itself.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | 8761 | |
| `INSTANCE_TTL_MS` | 30000 | remove an instance after this long without a heartbeat |

## Code tour (185 lines)

1. `src/server.js` – starts the HTTP server and the "remove silent instances" timer.
2. `src/registry.js` – the registry itself: `register`, `deregister`, `removeSilentInstances`, `list`.
3. `src/app.js` – the HTTP endpoints above.
4. `src/log.js` – one log line: `2026-10-28T15:04:05.123Z INFO  [service-discovery] Registered zombie-service/zombie-1 at ...`

## Contributing

Same workflow as the gateway: `main` and `development` protected, work on `feature/<issue>-name`,
PR into `development` using `.github/pull_request_template.md`. The **CI** workflow
(`.github/workflows/ci.yml`) runs `npm test` and builds the Docker image on every push/PR; tagging
`vX.Y.Z` on `main` pushes `<user>/service-discovery:X.Y.Z` and `:latest` to Docker Hub (needs the
`DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets).
