# Game Service

Real-time gameplay: lobbies, game sessions with a day/night cycle, and timed player actions.
Port **8082**. Spring Boot 3.5, Java 21, in-memory storage.

## Run

```bash
# with Docker
docker build -t game-service .
docker run -p 8082:8082 -e JWT_SECRET=<same-secret-as-player-service> game-service

# without Docker (JDK 21 + Maven)
mvn spring-boot:run
mvn test
```

| Env variable | Default | Meaning |
|---|---|---|
| `PORT` | 8082 | HTTP port |
| `JWT_SECRET` | dev-only value | must be **identical** to the Player Service secret |
| `GAME_MIN_PLAYERS_TO_START` | 2 | set to 1 to test alone |

You need a JWT from the Player Service (`POST /api/players/login`) for the (auth) endpoints.

## Code tour

- `model/GameSession` – the day/night cycle is **calculated from the start time**, not driven by a
  timer thread. With a 300 s cycle: 0–150 s is DAY 1, 150–300 s NIGHT 1, 300–450 s DAY 2, and so on.
- `model/TimedAction` – the same idea for actions: status and percent come from
  `started_at` / `completes_at`, so "chop bench for 10 minutes" needs no sleeping thread.
- `config/WebConfig` provides a `Clock` bean. Services ask it for the time, which makes the time
  logic easy to unit test (see `GameSessionTest`).
- `client/` – interfaces for World, Player and Resource services, with **mock** implementations
  that log what they would do. Replace a mock with an HTTP client later; the services don't change.

## Endpoints

(auth) = needs `Authorization: Bearer <jwt>`. The `player_id` / `host_id` in a body must match the JWT.

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/game/lobbies` (auth) | 201 | creator is the host and first player |
| GET | `/api/game/lobbies/{lobby_id}` (auth) | 200 | extra read endpoint for testing |
| POST | `/api/game/lobbies/{lobby_id}/join` (auth) | 200 | `{ "player_id": ... }` |
| POST | `/api/game/lobbies/{lobby_id}/leave` (auth) | 200 | host leaves → next player becomes host |
| POST | `/api/lobbies/{lobby_id}/start` (auth) | 201 | host only; map id comes from the (mocked) World Service |
| GET | `/api/sessions/{session_id}` | 200 | snapshot: cycle, phase, `cycle_ends_at`, players |
| POST | `/api/sessions/{session_id}/end` (auth) | 200 | host only; survivors get XP (mocked call to Player Service) |
| POST | `/api/game/lobbies/{lobby_id}/actions` (auth) | 202 | starts a timed action, returns immediately |
| GET | `/api/sessions/{session_id}/actions/{action_id}` | 200 | status, percent, and `reward` when completed |
| DELETE | `/api/game/actions/{action_id}` (auth) | 204 | only your own in-progress action |

Action types: `chop_bench`, `scavenge_canteen`, `clear_room`, `barricade_room`, `repair_base`, `upgrade_base`.

### Example

```bash
curl -X POST localhost:8082/api/game/lobbies/$LOBBY/actions \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"player_id":"'$PLAYER'","action_type":"chop_bench","target":"bench-room-A1","duration_seconds":600}'
# 202 {"action_id":"action-...","status":"in_progress","started_at":"...","completes_at":"..."}
```

## Errors

| Status | `error` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_JSON`, `UNKNOWN_ACTION_TYPE` | bad input |
| 401 | `MISSING_TOKEN`, `INVALID_TOKEN` | no/bad JWT |
| 403 | `NOT_YOUR_PLAYER`, `NOT_LOBBY_HOST`, `NOT_IN_LOBBY`, `NOT_YOUR_ACTION` | not allowed |
| 404 | `LOBBY_NOT_FOUND`, `SESSION_NOT_FOUND`, `ACTION_NOT_FOUND`, `PLAYER_NOT_IN_LOBBY` | unknown id |
| 409 | `NOT_ENOUGH_PLAYERS`, `SESSION_ALREADY_STARTED`, `SESSION_NOT_RUNNING`, `SESSION_ALREADY_FINISHED`, `LOBBY_FULL`, `ALREADY_IN_LOBBY`, `ACTION_ALREADY_ACTIVE`, `ACTION_NOT_IN_PROGRESS` | conflict |

## Not in this iteration

WebSockets, zombie actions (`/zombie-actions`) and advancing the cycle manually (`/cycle`).

## Lab 3: status, logs, Service Discovery

- `GET /status` – is this instance alive: `{"service", "instance_id", "status": "UP",
  "uptime_seconds", "requests_total"}`. Same shape in every service.
- Logs: one line when a request arrives and one when it leaves (status + duration), one line per
  business change and per rejected request. Every line shows the gateway's `X-Request-Id`.
- With `SERVICE_DISCOVERY_URL` set (in compose: `http://service-discovery:8761`) the instance
  registers itself on startup, sends a heartbeat every 10 s and deregisters on shutdown.
  Without it (laptop, tests) nothing is registered.
- Every response carries `X-Instance-Id`, so you can see which instance answered.

