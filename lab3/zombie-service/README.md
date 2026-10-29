# zombie-service

Stores **zombie types** (Tourist Zombie, Professor Zombie, ...) and their behaviour rules.
Java 17, Spring Boot, PostgreSQL (in Docker).

## Run

This service is started together with all the others from the project root
(`docker-compose.yml` contains every service, including `zombie-db`):

```bash
cd ..                    # project root
cp .env.example .env     # then set ZOMBIE_DB_USER / ZOMBIE_DB_PASSWORD (and the other values)
python run.py docker     # or: docker compose up --build -d
```

API (through the gateway): http://localhost:8080/api/zombies/types
Stop with `python run.py stop` (data is kept in the `zombie-db-data` volume).

`.env` is ignored by Git, so the real password is never committed.
The service reads three environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD` (set by the root `docker-compose.yml`).

Run only the tests (no Docker needed): `./mvnw test` (Windows: `mvnw.cmd test`).

## Endpoints

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/zombies/types` | 201 + the created type |
| GET | `/api/zombies/types` | 200 + list of types |
| GET | `/api/zombies/types/{type_id}` | 200 + the type |
| PATCH | `/api/zombies/types/{type_id}` | 200 + the updated type |
| DELETE | `/api/zombies/types/{type_id}` | 200 + `{"type_id": "...", "deleted": true}` |

Errors always look like `{"error": "message"}`:
404 unknown ID, 409 `type_id` already exists, 400 invalid fields / invalid JSON / unknown field.

Example body (`examples/tourist.json`):

```json
{
  "type_id": "tourist_zombie",
  "name": "Tourist Zombie",
  "health": 80,
  "damage": 12,
  "speed": 1.5,
  "loot_table_id": "loot-tourist-01",
  "behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 5 },
    { "trigger": "player_has_items", "action": "steal", "max_items": 2 }
  ]
}
```

Rules: `type_id` is chosen by the client; `health > 0`; `damage` and `speed >= 0`.
PATCH changes only the fields you send; `type_id` cannot be changed; `behaviours`, if sent, replaces the whole list.

## Publish

```bash
docker build -t YOUR_DOCKERHUB_USERNAME/zombie-service:1.1.0 .
docker push YOUR_DOCKERHUB_USERNAME/zombie-service:1.1.0
```

See `LEARN.md` for a walkthrough of the code.

## Lab 3: status, logs, Service Discovery

- `GET /status` – is this instance alive: `{"service", "instance_id", "status": "UP",
  "uptime_seconds", "requests_total"}`. Same shape in every service.
- Logs: one line when a request arrives and one when it leaves (status + duration), one line per
  business change and per rejected request. Every line shows the gateway's `X-Request-Id`.
- With `SERVICE_DISCOVERY_URL` set (in compose: `http://service-discovery:8761`) the instance
  registers itself on startup, sends a heartbeat every 10 s and deregisters on shutdown.
  Without it (laptop, tests) nothing is registered.
- Every response carries `X-Instance-Id`, so you can see which instance answered.
- Runs as **3 replicas** in `docker-compose.yml`. Creating the same `type_id` on two instances at the
  same moment can't overwrite any more: the second insert is refused by PostgreSQL → `409`
  (`ZombieType implements Persistable`, see `ScalingSafetyTest`).
