# Player Service

Global identity and progression of players: registration, login (JWT), profile, XP/levels,
persistent inventory and friends. Port **8081**. Spring Boot 3.5, Java 21, in-memory storage.

## Run

```bash
# with Docker
docker build -t player-service .
docker run -p 8081:8081 -e JWT_SECRET=<at-least-32-characters> player-service

# without Docker (JDK 21 + Maven)
mvn spring-boot:run
mvn test
```

| Env variable | Default | Meaning |
|---|---|---|
| `PORT` | 8081 | HTTP port |
| `JWT_SECRET` | dev-only value | HMAC key for JWTs, ≥ 32 chars, same as Game Service |
| `JWT_EXPIRATION_MINUTES` | 60 | token lifetime |

## Code tour

Read in this order: `controller` → `service` → `repository` → `model`.

- `dto/` – the exact JSON of the contract as Java `record`s. JSON is snake_case through
  `spring.jackson.property-naming-strategy=SNAKE_CASE`, so `playerId` is sent as `player_id`.
- `security/` – `@RequiresAuth` marks endpoints that need `Authorization: Bearer <jwt>`;
  `AuthInterceptor` checks the token and passes the caller's id to the controller.
- `exception/` – services throw `ApiException(status, code, message)`;
  `GlobalExceptionHandler` turns it into `{ "error": "CODE", "message": "..." }`.
- `repository/IdempotencyStore` – internal operations with the same `operation_id` run only once.
- `model/Progression` – the level rule: every 500 XP = 1 level.

## Endpoints

(auth) = needs `Authorization: Bearer <jwt>`. (own) = the JWT must belong to `{player_id}`.

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/players/register` | 201 | new player gets a starter kit (3 coffee, energy drink, 2 sandwiches, axe) |
| POST | `/api/players/login` | 200 | returns `jwt`, `player_id`, `roles` |
| GET | `/api/players/{player_id}` (auth) | 200 | public profile |
| PATCH | `/api/players/{player_id}` (auth, own) | 200 | `title`, `avatar` (both optional) |
| DELETE | `/api/players/{player_id}` (auth, own) | 204 | also removes inventory and friendships |
| GET | `/api/players/{player_id}/xp` | 200 | `xp`, `level`, `xp_to_next_level` |
| PATCH | `/api/players/{player_id}/xp` | 200 | internal, idempotent via `operation_id` |
| GET | `/api/players/{player_id}/inventory` (auth, own) | 200 | list of `item_id`, `code`, `name`, `count` |
| POST | `/api/players/{player_id}/inventory` | 201 | internal, idempotent; same `code` is stacked |
| PATCH | `/api/players/{player_id}/inventory/{item_id}` | 200 | `delta: -1` = consume; record stays at 0 |
| POST | `/api/players/{player_id}/friends` (auth, own) | 201 | friend request, status `PENDING` |
| GET | `/api/players/{player_id}/friends` (auth) | 200 | presence `online`/`offline` + `friendship_id` |
| DELETE | `/api/players/{player_id}/friends/{friendship_id}` (auth, own) | 204 | remove friend / cancel request |

### Example

```bash
curl -X POST localhost:8081/api/players/register -H "Content-Type: application/json" \
  -d '{"username":"undead_survivor","email":"player@faf.university","password":"secret123"}'
# 201 {"player_id":"player-...","username":"undead_survivor","level":1,"xp":0}

curl -X POST localhost:8081/api/players/login -H "Content-Type: application/json" \
  -d '{"email":"player@faf.university","password":"secret123"}'
# 200 {"jwt":"eyJ...","player_id":"player-...","roles":["player"]}
```

## Errors

| Status | `error` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_JSON`, `CANNOT_FRIEND_YOURSELF` | bad input |
| 401 | `MISSING_TOKEN`, `INVALID_TOKEN`, `INVALID_CREDENTIALS` | no/bad JWT, wrong login |
| 403 | `NOT_YOUR_ACCOUNT` | JWT belongs to another player |
| 404 | `PLAYER_NOT_FOUND`, `ITEM_NOT_FOUND`, `FRIENDSHIP_NOT_FOUND` | unknown id |
| 409 | `EMAIL_ALREADY_USED`, `USERNAME_TAKEN`, `NOT_ENOUGH_ITEMS`, `FRIENDSHIP_ALREADY_EXISTS` | conflict |

## Notes on the contract

- Passwords are sent in plain text over HTTPS and hashed here with BCrypt (the contract shows
  `<hashed_password>` in the register example; hashing on the server is the safer choice).
- The GET `/xp` example (`xp_to_next_level: 580` at 3420 XP) can't be true together with the PATCH
  example (3670 XP → level 8). We follow the PATCH one: 500 XP per level.
- Trades (`/api/trades`) are not implemented in this iteration.

## Lab 3: status, logs, Service Discovery

- `GET /status` – is this instance alive: `{"service", "instance_id", "status": "UP",
  "uptime_seconds", "requests_total"}`. Same shape in every service.
- Logs: one line when a request arrives and one when it leaves (status + duration), one line per
  business change and per rejected request. Every line shows the gateway's `X-Request-Id`.
- With `SERVICE_DISCOVERY_URL` set (in compose: `http://service-discovery:8761`) the instance
  registers itself on startup, sends a heartbeat every 10 s and deregisters on shutdown.
  Without it (laptop, tests) nothing is registered.
- Every response carries `X-Instance-Id`, so you can see which instance answered.
