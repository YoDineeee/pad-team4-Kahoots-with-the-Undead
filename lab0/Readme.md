# PAD-team1

# Topic - In Kahoots with the Undead - Communication Contract

This document outlines the communication contracts for the microservices within the **"In Kahoots with the Undead"** platform a zombie-apocalypse survival game set during the FAF university exam season. All services that require user authentication or player-specific information will validate requests using a **JWT (JSON Web Token)** provided in the `Authorization` header.

## Overview

The platform is a distributed set of microservices simulating a persistent, multiplayer survival experience. Players wake up in FAF Cab, scavenge resources, fight zombies, attend PBL presentations, and (hopefully) survive the exam season. Each microservice encapsulates a specific game domain to ensure modularity, independence, and maintainability.

Microservices are implemented using multiple technologies to optimize performance and leverage language-specific strengths:

- **Elixir/Phoenix:** Player Service (Dima), Game Service (Dima), Exam Service (Alexandra), World Service (Alexandra)
- **Ruby/Sinatra** Zombie Service (Ivan), Resource Service (Ivan), Base Service (Sasha), Crafting Service (Sasha)

## Technologies & Communication Patterns

| Owner                    | Services                         | Language & Framework | Database   | Communication Patterns                                                | Motivation & Trade-offs                                                                                                                                                                                                                                                                                                                                                      |
| ------------------------ | -------------------------------- | -------------------- | ---------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dmitrii Belih            | Player Service, Game Service     | Elixir, Phoenix      | PostgreSQL | REST with JWT auth, WebSockets (Phoenix Channels), async timers, CQRS | Phoenix's secure auth plugs and channels are ideal for player identity and real-time gameplay. Elixir's lightweight processes and OTP supervision handle high-concurrency lobbies, day/night cycles and live action progress over WebSockets.                                                                                                                                |
| Alexandra Mihalevschi    | Exam Service, World Service      | Elixir, Phoenix      | PostgreSQL | REST, Async event-driven updates (PubSub), notifications, CQRS        | Elixir's OTP supervision gives us fault-tolerant exam grading workflows and reliable world-state mutations. Phoenix PubSub makes fanning out `ExamPassed` events to the World Service and achievements effortless.                                                                                                                                                           |
| Ivan Rudenco             | Zombie Service, Resource Service | Ruby, Sinatra        | PostgreSQL | REST, Idempotent operations, Async notification queues, CQRS          | Sinatra on Rack keeps zombie-config endpoints lightweight and read-friendly. `dry-schema` validates inbound payloads at the controller boundary. Idempotent resource operations (gain / consume / transfer) are protected by a unique `event_id` index and executed inside `Sequel.transaction { ... }` blocks against PostgreSQL.                                           |
| Bujor - Cobili Alexandra | Base Service, Crafting Service   | Ruby, Sinatra        | PostgreSQL | REST, Atomic transactions, Async event-driven updates, CQRS           | Sinatra modular apps (`Sinatra::Base` subclasses) keep base upgrades, Kiki rewards and atomic recipe resolution isolated and unit-testable. `Sequel.transaction` makes consume-then-credit flows atomic against PostgreSQL, while `dry-events` (or `wisper`) publishers emit `inventory.changed` events that the Player Service consumes via Sidekiq (or `good_job`) queues. |

## Architectural Diagram

![Architectural Diagram](lab0/architecture/flowchart.jpg)


The API Gateway is the single entry point to the platform. Clients never call a service directly: every request goes to the Gateway, which checks the caller's JWT and routes the request by its path to the service that owns it. Service-to-service calls take the same route. When Exam Service tells Player Service to award XP, for example, it calls the Gateway, and the Gateway forwards the request to Player Service. Each of the eight services owns its domain and its own PostgreSQL database, and no service reads another's database. The Game Service sits at the center of the live game loop.

| Connection | Protocol |
| --- | --- |
| Client → Gateway → service (every `/api/...` route) | REST over HTTP |
| Service → Gateway → service (e.g. Exam → Player, World → Resource, Zombie → World) | REST over HTTP, with a service-to-service JWT |
| Client ↔ Gateway ↔ Game Service (`/ws/game/lobbies/{lobby_id}`) | WebSocket, negotiated at the Gateway and proxied to the Game Service |

The WebSocket carries live game events, such as action progress, zombie attacks and day/night changes (see [WebSocket Connection](#websocket-connection)). Everything else is REST.

***

## Running the platform

Everything runs from images published on Docker Hub. You do not need Ruby, Elixir or a
local Postgres, only Docker and Docker Compose.

```bash
cp .env.example .env     # then fill in every secret
docker compose up -d
```

Compose refuses to start while a secret is missing, so you cannot boot with empty
credentials by accident. Generate each one with `openssl rand -hex 32`. `.env` is
git-ignored and must never be committed.

Each service brings up its own Postgres on its own named volume and migrates itself on
first boot. The Ruby services also seed their catalog on boot; exam-service and
world-service start empty, and their demo data is loaded separately (see
[Database scripts](#database-scripts)). Nothing else to run.

### Published images

| Service | Image | Owner |
| --- | --- | --- |
| base-service | [`cobili/base-service:2.0.2`](https://hub.docker.com/r/cobili/base-service) | Bujor-Cobili Alexandra |
| crafting-service | [`cobili/crafting-service:2.0.2`](https://hub.docker.com/r/cobili/crafting-service) | Bujor-Cobili Alexandra |
| player-service | [`dimon1/player-service:0.1.1`](https://hub.docker.com/r/dimon1/player-service) | Dmitrii Belih |
| game-service | [`dimon1/game-service:0.1.0`](https://hub.docker.com/r/dimon1/game-service) | Dmitrii Belih |
| zombie-service | [`tukaram40k/zombie-service:0.1.0`](https://hub.docker.com/r/tukaram40k/zombie-service) | Ivan Rudenco |
| resource-service | [`tukaram40k/resource-service:0.1.0`](https://hub.docker.com/r/tukaram40k/resource-service) | Ivan Rudenco |
| exam-service | [`alexandramihalevschi/exam-service:2.0.1`](https://hub.docker.com/r/alexandramihalevschi/exam-service) | Alexandra Mihalevschi |
| world-service | [`alexandramihalevschi/world-service:2.0.1`](https://hub.docker.com/r/alexandramihalevschi/world-service) | Alexandra Mihalevschi |

The tag is the service version, so `cobili/base-service:2.0.2` is version 2.0.2.
`docker-compose.yaml` pins the version rather than `latest`, so a run is reproducible.

Every service in the contract is now in `docker-compose.yaml`; there is nothing left to
uncomment.

#### What each image needs

| Service | Required env (from `.env`) | Notes |
| --- | --- | --- |
| base-service, crafting-service | `*_DB_PASSWORD`, `JWT_SECRET`, `SERVICE_JWT_SECRET`, `GATEWAY_SECRET` | They no longer read `Authorization`: the gateway validates the token and they authorize from `X-Gateway-Secret` plus `X-Player-Id` / `X-Roles` / `X-Service-Name`. Reach them through the gateway, not on 4007/4008. `MOCK_MODE` optional, defaults to `true`; `GATEWAY_URL` defaults to `http://gateway:8080` |
| zombie-service | `ZOMBIE_DB_PASSWORD`, `JWT_SECRET`, `SERVICE_JWT_SECRET` | `MOCK_MODE` optional, defaults to `true`; also calls Player, Resource and World Services |
| resource-service | `RESOURCE_DB_PASSWORD`, `JWT_SECRET`, `SERVICE_JWT_SECRET` | Service-to-service JWT authentication |
| exam-service, world-service | `*_POSTGRES_PASSWORD`, `SECRET_KEY_BASE`, `SERVICE_JWT_SECRET`, `GATEWAY_SECRET` | They don't verify JWTs: the API Gateway does, and forwards the caller as headers with `X-Gateway-Secret`, so a request without the right `GATEWAY_SECRET` gets `401`. Outbound calls go through the Gateway at `GATEWAY_URL` (default `http://gateway:8080`). The image migrates on start; no `command:` override is needed |
| player-service | `PLAYER_DB_PASSWORD`, `PLAYER_SECRET_KEY_BASE`, `JWT_SECRET`, `SERVICE_JWT_SECRET` | It calls the shared service secret `INTERNAL_JWT_SECRET`, so `docker-compose.yaml` maps `SERVICE_JWT_SECRET` onto it — every service still signs the same tokens. Its entrypoint waits on Postgres and migrates before starting, so it also needs `POSTGRES_HOST` and the credentials as separate variables |
| game-service | `GAME_DB_PASSWORD`, `GAME_SECRET_KEY_BASE`, `JWT_SECRET` | Has no service secret of its own: it checks for a `service` role in a token signed with `JWT_SECRET`. `GAME_BYPASS_AUTH` defaults to `false` here — the service's own default is `true`, which accepts unsigned requests. Its release start script ships without the executable bit, so `docker-compose.yaml` runs it as `sh /app/bin/server` |

#### Building exam-service / world-service from source

Each lives in its own private repository, linked here as a git submodule. Their CI publishes
`<version>` and `latest` (linux/amd64 and linux/arm64) on every merge to `main`, so nobody
pushes by hand. To build one locally, with access to it:

```bash
cd exam-service                       # or world-service
docker build -t alexandramihalevschi/exam-service:<version> .
```

After a release, bump the tag in `docker-compose.yaml` and in the table above. Each repository also
has its own `docker-compose.yml` (app + Postgres), which builds from source for running
that service alone. See its README.

### Ports

| Service | Port |
| --- | --- |
| player-service | 4001 |
| game-service | 4002 |
| zombie-service | 4003 |
| world-service | 4004 |
| exam-service | 4005 |
| resource-service | 4006 |
| base-service | 4007 |
| crafting-service | 4008 |

base-service, crafting-service, zombie-service, resource-service and game-service answer
`GET /health`; player-service answers `GET /api/health`:

```bash
curl http://localhost:4007/health
```

```json
{"status":"ok","service":"base-service","version":"0.1.0","database":"up","mock_mode":true}
```

The other Ruby services use their own ports:

```bash
curl http://localhost:4003/health  # zombie-service
curl http://localhost:4006/health  # resource-service
curl http://localhost:4002/health  # game-service
curl http://localhost:4001/api/health  # player-service, note the /api prefix
```

game-service reports whether auth enforcement is on, which is worth checking after a boot:

```json
{"status":"ok","version":"0.1.0","service":"game-service","database":"up","auth_bypass":false,"running_sessions":0}
```

exam-service and world-service answer `GET /health` too, with their database status:

```bash
curl http://localhost:4005/health  # exam-service
curl http://localhost:4004/health  # world-service
```

```json
{"status":"ok","version":"2.0.1","service":"exam-service","database":"up"}
```

Their API routes answer `401` when called directly, because they only accept requests that
came through the API Gateway (see [What each image needs](#what-each-image-needs)).

### Running before the whole team is up

player-service ships with mock clients of its own, under `lib/player_service/mocks/`, and
records every call it would have made in a `mock_invocations` table, so its own endpoints work
before the services it talks to are reachable.

game-service has no `MOCK_MODE`. It has `BYPASS_AUTH`, which is a different thing: it disables
token checks rather than stubbing outbound calls, and `docker-compose.yaml` turns it off. Its
outbound calls live in `lib/game_service/clients/` and are real HTTP calls to the other
services in this compose file.

base-service, crafting-service and zombie-service ship with `MOCK_MODE`. While it is `true` they use
built-in stand-ins for the services they depend on, so they run and can be tested on
their own. Set it to `false` once the real services are in the compose file.

exam-service and world-service have no toggle. Every outbound call they make is a real
request through the API Gateway (`GATEWAY_URL`), signed with a service token. Until the
Gateway is in this compose file the calls fail; the failure is logged and never undoes the
change that triggered it (a graded exam stays graded):

| From | To | Call |
| --- | --- | --- |
| exam-service | Player Service | `PATCH /api/players/{player_id}/xp` |
| exam-service | World Service | `POST /api/world/unlocks` |
| exam-service | Crafting Service | `POST /api/crafting/players/{player_id}/unlocks` |
| world-service | Resource Service | `POST /api/resources/points` |
| world-service | Crafting Service | `POST /api/crafting/players/{player_id}/unlocks` |

Watch them with `docker compose logs -f exam-service world-service`.

### Testing

`postman/` holds a collection per service. Import them, or run them headless:

```bash
newman run postman/base-service.postman_collection.json \
  --env-var playerToken="$PLAYER_TOKEN" --env-var serviceToken="$SERVICE_TOKEN"
newman run postman/zombie-service.postman_collection.json \
  --env-var playerToken="$PLAYER_TOKEN" --env-var serviceToken="$SERVICE_TOKEN"
newman run postman/resource-service.postman_collection.json \
  --env-var serviceToken="$SERVICE_TOKEN"
```

Tokens come from the service itself:

```bash
docker compose exec base-service bundle exec rake token:player
docker compose exec base-service bundle exec rake token:service
docker compose exec zombie-service bundle exec rake token:player
docker compose exec zombie-service bundle exec rake token:service
docker compose exec resource-service bundle exec rake token:service
```

The exam-service and world-service collections sign their own tokens in a pre-request
script. They only need the same secrets as your `.env`:

```bash
newman run postman/exam-service.postman_collection.json \
  --env-var jwt_secret="$JWT_SECRET" --env-var service_jwt_secret="$SERVICE_JWT_SECRET"
newman run postman/world-service.postman_collection.json \
  --env-var jwt_secret="$JWT_SECRET" --env-var service_jwt_secret="$SERVICE_JWT_SECRET"
```

player-service and game-service have collections too, copied from each service's own
repository with the base URL pointed at the port the shared stack publishes:

```bash
newman run postman/player-service.postman_collection.json --env-var base_url=http://localhost:4001
newman run postman/game-service.postman_collection.json \
  --env-var baseUrl=http://localhost:4002 \
  --env-var token="$PLAYER_TOKEN" --env-var serviceToken="$SERVICE_TOKEN"
```

player-service mints its own tokens through the collection: it registers, logs in and reuses
the JWT. game-service needs real tokens passed in, because `BYPASS_AUTH` is `false` here.

See `postman/README.md` for details and last-run results.

### Database scripts

`deploy/db/` holds the schema and the seed data for all eight services as plain SQL, for
anyone who wants to inspect the tables or load them into their own Postgres. The Ruby
containers migrate and seed themselves, so their files are for reference.

exam-service and world-service migrate on boot but don't seed. To load their demo data
into the running stack, pipe the seed dump into each database container:

```bash
docker compose exec -T exam-db psql -U exam -d exam_service < deploy/db/exam-service-seed.sql
docker compose exec -T world-db psql -U world -d world_service < deploy/db/world-service-seed.sql
```

player-service's seed dump is deliberately partial: its own seed script aborts partway, so
only players and their inventory are in there. `deploy/db/README.md` explains why and what to
fix. game-service migrates and seeds itself on boot like the Ruby services.

Only the seed file is needed here, because the migrations already created the tables. On
Windows PowerShell, `<` doesn't work, so use
`Get-Content deploy\db\exam-service-seed.sql | docker compose exec -T exam-db psql -U exam -d exam_service`.

## Authentication

All endpoints marked with **Headers: `Authorization: Bearer <jwt>`** require a valid JWT issued by the **Player Service**. The token contains:

- `sub` the player UUID
- `roles` e.g. `["player"]`, `["player", "moderator"]`

Cross-service calls (e.g. Game Service → Resource Service) use a dedicated **service-to-service JWT** signed with a shared internal secret.

---

## 1. Player Service

Handles the **global identity and progression** of players: registration, authentication, profiles, friends, online presence, XP, levels and progression. Also owns each player's **persistent inventory** of consumables, cosmetics and other player-owned objects.

### Responsibilities

- Register and authenticate players.
- Issue JWTs upon successful login.
- Manage profiles, XP, levels and progression.
- Manage friends, friend requests and online presence.
- Own the persistent player inventory (consumables, cosmetics, equipment).

### Endpoints

#### Register a Player

`POST /api/players/register`
Description: Creates a new player account.
Payload:

```json
{
  "username": "undead_survivor",
  "email": "player@faf.university",
  "password": "<hashed_password>"
}
```

Success Response (201 Created):

```json
{
  "player_id": "player-uuid-123",
  "username": "undead_survivor",
  "level": 1,
  "xp": 0
}
```

#### Login

`POST /api/players/login`
Description: Authenticates a player and returns a JWT.
Payload:

```json
{
  "email": "player@faf.university",
  "password": "<plain_password>"
}
```

Success Response (200 OK):

```json
{
  "jwt": "<jwt_token>",
  "player_id": "player-uuid-123",
  "roles": ["player"]
}
```

#### Get Player Profile

`GET /api/players/{player_id}`
Description: Retrieves public profile information for a player.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{
  "player_id": "player-uuid-123",
  "username": "undead_survivor",
  "level": 7,
  "xp": 1340,
  "university": "FAF",
  "title": "Survivor of the Pumpkin"
}
```

#### Update Player Profile

`PATCH /api/players/{player_id}`
Description: Updates the player's public profile.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{
  "title": "Pumpkin Slayer",
  "avatar": "axe_wielding"
}
```

#### Delete Player Profile

`DELETE /api/players/{player_id}`
Description: Deletes the account. Inventory and active trades are released first.

Success Response (204 No Content)

#### Level XP

`GET /api/players/{player_id}/xp`
Description: Reads the progression counters.
Payload: —

Success Response (200 OK):

```json
{
  "player_id": "player-uuid-123",
  "xp": 3420,
  "level": 7,
  "xp_to_next_level": 580
}
```

#### Update XP

`PATCH /api/players/{player_id}/xp`
Description: Adds or removes XP. Called by Game Service (kills, finished actions), Exam Service (passed exams) and Tourist Zombies (stealing XP). Idempotent through `operation_id`.
Payload:

```json
{
  "operation_id": "xp-op-uuid-555",
  "delta": 250,
  "reason": "EXAM_PASSED",
  "source_service": "exam-service"
}
```

Success Response (200 OK):

```json
{
  "player_id": "player-uuid-123",
  "xp": 3670,
  "level": 8,
  "leveled_up": true
}
```

#### Get Player Inventory

`GET /api/players/{player_id}/inventory`
Description: Returns the player's full inventory.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  { "item_id": "coffee-01", "name": "Coffee", "count": 3 },
  { "item_id": "energy-01", "name": "Energy Drink", "count": 1 },
  { "item_id": "sandwich-01", "name": "Davidan Sandwich", "count": 2 },
  { "item_id": "axe-01", "name": "Improvised Axe", "count": 1 }
]
```

#### Add Item to Inventory **[internal]**

`POST /api/players/{player_id}/inventory`
Description: Puts an item in the inventory. Called by Crafting Service (crafted object), World Service (Kiki reward) and Game Service (loot). Idempotent through `operation_id`.
Payload:

```json
{
  "operation_id": "grant-uuid-901",
  "code": "ZOMBIE_DETECTOR",
  "type": "equipment",
  "quantity": 1,
  "source": "crafting-service"
}
```

Success Response (201 Created):

```json
{
  "item_id": "item-uuid-12",
  "code": "ZOMBIE_DETECTOR",
  "quantity": 1
}
```

#### Update / Consume Item

`PATCH /api/players/{player_id}/inventory/{item_id}`
Description: Changes the quantity of an item. Consuming a Coffee is `delta: -1`; the record stays at quantity 0 instead of being deleted (CRU only).
Payload:

```json
{
  "operation_id": "consume-uuid-33",
  "delta": -1,
  "reason": "CONSUMED"
}
```

Success Response (200 OK):

```json
{
  "item_id": "item-uuid-1",
  "code": "COFFEE",
  "quantity": 2
}
```

### Add Friend

`POST /api/players/{player_id}/friends`
Description: Sends a friend request.
Payload:

```json
{
  "target_player_id": "player-uuid-456"
}
```

Success Response (201 Created):

```json
{
  "friendship_id": "friend-uuid-10",
  "target_player_id": "player-uuid-456",
  "status": "PENDING"
}
```

#### Get Friends List

`GET /api/players/{player_id}/friends`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "player_id": "player-uuid-456", "username": "kiki", "status": "online" }]
```

#### Remove Friend

`DELETE /api/players/{player_id}/friends/{friendship_id}`
Description: Removes the friendship or cancels the request.
Payload: —

Success Response (204 No Content)

#### Create a Trade

`POST /api/trades`
Description: Opens a trade offer towards another player. Works across lobbies/universities. Ownership of every offered item is verified here and the items are locked until the trade resolves.
Payload:

```json
{
  "from_player_id": "player-uuid-123",
  "to_player_id": "player-uuid-456",
  "offered_items": [{ "item_id": "item-uuid-9", "quantity": 1 }],
  "requested_items": [{ "code": "ENERGY_DRINK", "quantity": 2 }]
}
```

Success Response (201 Created):

```json
{
  "trade_id": "trade-uuid-77",
  "status": "PENDING",
  "expires_at": "2026-09-10T14:15:00Z"
}
```

Errors: `403 ITEM_NOT_OWNED`, `409 ITEM_ALREADY_LOCKED`

#### Accept a Trade

`POST /api/trades/{trade_id}/accept`
Description: Executes the transfer atomically — both sides move or neither does. On failure everything is rolled back and the items unlocked.
Payload:

```json
{
  "player_id": "player-uuid-456"
}
```

Success Response (200 OK):

```json
{
  "trade_id": "trade-uuid-77",
  "status": "COMPLETED",
  "completed_at": "2026-09-10T14:06:40Z"
}
```

Errors: `409 TRADE_EXPIRED`, `409 REQUESTED_ITEMS_MISSING`

#### Reject / Cancel a Trade

`POST /api/trades/{trade_id}/reject`
Description: Cancels the offer and unlocks the items. Callable by either side.
Payload: —

Success Response (200 OK):

```json
{
  "trade_id": "trade-uuid-77",
  "status": "REJECTED"
}
```

#### List Trades

`GET /api/trades?player_id={player_id}&status=PENDING`
Description: Returns the trades a player is involved in.
Payload: —

Success Response (200 OK):

```json
{
  "trades": [
    {
      "trade_id": "trade-uuid-77",
      "from_player_id": "player-uuid-123",
      "to_player_id": "player-uuid-456",
      "status": "PENDING"
    }
  ]
}
```

---

## 2. Game Service

The **central real-time gameplay service**. Owns game sessions/lobbies, the day/night cycle, session timers and timed player actions. Also coordinates player trading, zombie behavior and short-lived game events. **Does not permanently own the university map or player inventory.**

### Responsibilities

- Create, join and leave game lobbies.
- Run the day/night cycle and tick timers.
- Start asynchronous actions (chop benches, scavenge canteen, clear room, barricade, repair/upgrade base).
- Push live progress over WebSockets.
- Coordinate trading between players (possibly across universities).
- Verify ownership and perform atomic inventory transfers.
- Manage short-lived zombie behavior during a cycle.
- Notify players and services when actions, attacks or cycles finish.

### REST Endpoints

#### Create a Lobby

`POST /api/game/lobbies`
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{
  "host_id": "player-uuid-123",
  "university": "FAF",
  "max_players": 8,
  "name": "Cab Survivors"
}
```

Success Response (201 Created):

```json
{
  "lobby_id": "lobby-uuid-789",
  "host_id": "player-uuid-123",
  "university": "FAF",
  "day_cycle": 1,
  "phase": "day",
  "players": ["player-uuid-123"]
}
```

#### Join a Lobby

`POST /api/game/lobbies/{lobby_id}/join`
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "player_id": "player-uuid-456" }
```

#### Leave a Lobby

`POST /api/game/lobbies/{lobby_id}/leave`
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "player_id": "player-uuid-456" }
```

#### Start a Session

`POST /api/lobbies/{lobby_id}/start`
Description: Host starts the game. Game Service pulls the map from World Service and the zombie configurations from Zombie Service, then begins the first day.
Payload:

```json
{
  "cycle_duration_seconds": 300,
  "semester_length_cycles": 14
}
```

Success Response (201 Created):

```json
{
  "session_id": "session-uuid-900",
  "lobby_id": "lobby-uuid-77",
  "cycle": "DAY",
  "cycle_number": 1,
  "map_id": "map-uuid-5",
  "started_at": "2026-09-10T14:00:00Z"
}
```

Errors: `403 NOT_LOBBY_HOST`, `409 NOT_ENOUGH_PLAYERS`

#### Get Session State

`GET /api/sessions/{session_id}`
Description: Snapshot of the running game — used on reconnect before the WebSocket takes over.
Payload: —

Success Response (200 OK):

```json
{
  "session_id": "session-uuid-900",
  "status": "RUNNING",
  "cycle": "NIGHT",
  "cycle_number": 4,
  "cycle_ends_at": "2026-09-10T14:25:00Z",
  "alive_zombies": 12,
  "players": [
    {
      "player_id": "player-uuid-123",
      "health": 80,
      "current_room_id": "room-uuid-3"
    }
  ]
}
```

#### End a Session

`POST /api/sessions/{session_id}/end`
Description: Ends the semester. Final XP is pushed to Player Service, results to Exam Service.
Payload: —

Success Response (200 OK):

```json
{
  "session_id": "session-uuid-900",
  "status": "FINISHED",
  "survivors": ["player-uuid-123"],
  "cycles_survived": 14
}
```

#### Start a Timed Action

`POST /api/game/lobbies/{lobby_id}/actions`
Description: Starts an async action like "chop bench for 10 minutes". Returns immediately; progress is pushed over WebSockets.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{
  "player_id": "player-uuid-123",
  "action_type": "chop_bench",
  "target": "bench-room-A1",
  "duration_seconds": 600
}
```

Success Response (202 Accepted):

```json
{
  "action_id": "action-uuid-001",
  "status": "in_progress",
  "started_at": "2026-09-05T10:00:00Z",
  "completes_at": "2026-09-05T10:10:00Z"
}
```

Error Response (409 Conflict):

```json
{ "error": "Player already has an active action." }
```

#### Get Action Status

`GET /api/sessions/{session_id}/actions/{action_id}`
Description: Polling fallback for when the WebSocket dropped. On completion the reward comes from Resource Service, this service only reports it.
Payload: —

Success Response (200 OK):

#### Cancel an Action

`DELETE /api/game/actions/{action_id}`
Headers: `Authorization: Bearer <jwt>`

#### Zombie Action **[internal]**

`POST /api/sessions/{session_id}/zombie-actions`
Description: An action issued by a zombie instance instead of a player, as part of the behavior Game Service drives during the current cycle. A `PROFESSOR` zombie triggers an exam request towards Exam Service; a `TOURIST` zombie steals resources or XP. Same async contract as player actions — `202` plus a WebSocket event — different actor. The `zombie_id` is only valid within the cycle that spawned it.
Payload:

```json
{
  "zombie_id": "zombie-uuid-31",
  "zombie_type": "PROFESSOR",
  "action": "ADMINISTER_EXAM",
  "target_player_id": "player-uuid-123",
  "room_id": "room-uuid-3"
}
```

Success Response (202 Accepted):

```json
{
  "action_id": "zaction-uuid-77",
  "status": "IN_PROGRESS",
  "exam_id": "exam-uuid-14",
  "deadline_seconds": 120
}
```

Tourist variant:

```json
{
  "zombie_id": "zombie-uuid-52",
  "zombie_type": "TOURIST",
  "action": "STEAL",
  "target_player_id": "player-uuid-123",
  "steal_target": "XP"
}
```

```json
{
  "action_id": "zaction-uuid-78",
  "status": "COMPLETED",
  "stolen": { "type": "XP", "amount": 40 }
}
```

#### Advance Day/Night Cycle

`POST /api/game/lobbies/{lobby_id}/cycle`
Headers: `Authorization: Bearer <service_jwt>`

### WebSocket Connection

`wss://api.undead/ws/game/lobbies/{lobby_id}?token=<JWT>`

#### Client Server Events

```json
{ "type": "join_lobby",  "lobby_id": "lobby-uuid-789" }
{ "type": "leave_lobby", "lobby_id": "lobby-uuid-789" }
{ "type": "action_progress_request", "action_id": "action-uuid-001" }
```

#### Server Client Events

```json
{ "type": "action_started",   "action_id": "...", "player_id": "...", "duration_seconds": 600 }
{ "type": "action_progress",  "action_id": "...", "elapsed_seconds": 120, "percent": 20 }
{ "type": "action_completed", "action_id": "...", "reward": [{ "item_id": "wood-01", "count": 4 }] }
{ "type": "zombie_attack",    "zombie_id": "...", "target_player_id": "...", "damage": 15 }
{ "type": "trade_offer",      "trade_id": "...", "from_player_id": "...", "offer": [...] }
{ "type": "cycle_changed",    "phase": "night", "day": 2 }
{ "type": "encounter_started","encounter_id": "...", "zombie_type": "professor_zombie" }
```

---

## 3. Zombie Service

Manages **zombie type definitions, behaviour configurations and per-instance state** within a game session (lobby). The service owns zombie instances, their current state/stats and their inventories, so that when a zombie steals resources from the world or from players those items are held by the zombie instance until it is killed. On death, the zombie's full inventory is transferred atomically to the player who killed it.

### Responsibilities

- Maintain a registry of zombie types with full CRUD: create, read, update and delete their stats, loot and behaviour configurations.
- CRUD zombie instances within a session: spawn, list, get, update and despawn.
- Track each zombie instance's current state (status, room, active behaviours).
- Track each zombie instance's stats (type, health, damage, ...).
- Own each zombie instance's inventory of stolen resources (read/update).
- Transfer a zombie's full inventory to the killing player on death.
- Provide zombie type data to Game Service for encounter resolution.

### Endpoints

#### Zombie Type CRUD

##### Create a Zombie Type

`POST /api/zombies/types`
Description: Creates a new zombie type definition with its stats and behaviour configuration. Used at season bootstrap and by moderators to add new enemy types.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "type_id": "tourist_zombie",
  "name": "Tourist Zombie",
  "health": 80,
  "damage": 12,
  "speed": 1.5,
  "loot_table_id": "loot-tourist-01",
  "behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 5 }
  ]
}
```

Success Response (201 Created):

```json
{
  "type_id": "tourist_zombie",
  "name": "Tourist Zombie",
  "health": 80,
  "damage": 12,
  "speed": 1.5,
  "loot_table_id": "loot-tourist-01",
  "behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 5 }
  ]
}
```

##### List Zombie Types

`GET /api/zombies/types`
Description: Returns all configured zombie types with their stats and behaviours.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
[
  {
    "type_id": "professor_zombie",
    "name": "Professor Zombie",
    "health": 100,
    "damage": 15,
    "speed": 1.2,
    "loot_table_id": "loot-professor-01"
  },
  {
    "type_id": "fast_zombie",
    "name": "Caffeinated Sprinter",
    "health": 60,
    "damage": 10,
    "speed": 3.0,
    "loot_table_id": "loot-sprinter-01"
  }
]
```

##### Get Zombie Type Details

`GET /api/zombies/types/{type_id}`
Description: Returns full details for a single zombie type, including its behaviour rules.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{
  "type_id": "professor_zombie",
  "name": "Professor Zombie",
  "health": 100,
  "damage": 15,
  "speed": 1.2,
  "loot_table_id": "loot-professor-01",
  "behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 5 },
    { "trigger": "player_in_room", "action": "steal", "max_items": 2 },
    { "trigger": "day_phase", "action": "hide", "location": "exam_hall" }
  ]
}
```

Error Response (404 Not Found):

```json
{ "error": "Zombie type not found." }
```

##### Update a Zombie Type

`PATCH /api/zombies/types/{type_id}`
Description: Edits a zombie type's stats or behaviour rules without recreating it, so existing instances keep referencing a valid `type_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "health": 95,
  "speed": 1.4,
  "behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 6 }
  ]
}
```

Success Response (200 OK):

```json
{
  "type_id": "professor_zombie",
  "name": "Professor Zombie",
  "health": 95,
  "damage": 15,
  "speed": 1.4,
  "loot_table_id": "loot-professor-01",
  "behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 6 },
    { "trigger": "player_in_room", "action": "steal", "max_items": 2 },
    { "trigger": "day_phase", "action": "hide", "location": "exam_hall" }
  ]
}
```

##### Delete a Zombie Type

`DELETE /api/zombies/types/{type_id}`
Description: Removes a zombie type from the registry. Refused while active instances still reference it.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "type_id": "tourist_zombie", "deleted": true }
```

Error Response (409 Conflict):

```json
{ "error": "Cannot delete a zombie type that still has active instances." }
```

#### Zombie Instance CRUD (in a Session)

##### List Zombie Instances in a Session

`GET /api/zombies/lobbies/{lobby_id}/instances`
Description: Returns all active zombie instances in a session (lobby), including their current state, stats and inventories.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
[
  {
    "zombie_id": "zombie-uuid-001",
    "type_id": "professor_zombie",
    "lobby_id": "lobby-uuid-789",
    "health": 100,
    "room_id": "exam-hall-3",
    "state": { "status": "idle", "phase": "day" },
    "inventory": [
      { "item_id": "coffee-01", "count": 1 },
      { "item_id": "metal-01", "count": 3 }
    ]
  }
]
```

##### Spawn a Zombie Instance (Create)

`POST /api/zombies/lobbies/{lobby_id}/instances`
Description: Spawns a new zombie instance in a session. Called by Game Service at cycle transitions or encounter triggers.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "type_id": "professor_zombie",
  "room_id": "exam-hall-3"
}
```

Success Response (201 Created):

```json
{
  "zombie_id": "zombie-uuid-002",
  "type_id": "professor_zombie",
  "lobby_id": "lobby-uuid-789",
  "health": 100,
  "room_id": "exam-hall-3",
  "state": { "status": "idle", "phase": "day" },
  "inventory": []
}
```

##### Get a Zombie Instance (Read)

`GET /api/zombies/lobbies/{lobby_id}/instances/{zombie_id}`
Description: Returns a single zombie instance's full record — state, stats and inventory.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "type_id": "professor_zombie",
  "lobby_id": "lobby-uuid-789",
  "health": 60,
  "room_id": "canteen",
  "state": { "status": "stealing", "target_player_id": "player-uuid-123", "phase": "day" },
  "inventory": [
    { "item_id": "coffee-01", "count": 1 },
    { "item_id": "metal-01", "count": 3 }
  ]
}
```

Error Response (404 Not Found):

```json
{ "error": "Zombie instance not found." }
```

##### Update a Zombie Instance (Update)

`PATCH /api/zombies/lobbies/{lobby_id}/instances/{zombie_id}`
Description: Updates structural instance fields such as the room the zombie currently occupies. For state, stats or inventory changes, use the dedicated `/state`, `/stats` and `/inventory` endpoints below.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "room_id": "library-2" }
```

Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "type_id": "professor_zombie",
  "lobby_id": "lobby-uuid-789",
  "health": 60,
  "room_id": "library-2",
  "state": { "status": "stealing", "target_player_id": "player-uuid-123", "phase": "day" },
  "inventory": [
    { "item_id": "coffee-01", "count": 1 },
    { "item_id": "metal-01", "count": 3 }
  ]
}
```

##### Despawn a Zombie Instance (Delete)

`DELETE /api/zombies/lobbies/{lobby_id}/instances/{zombie_id}`
Description: Removes a zombie instance from the session without transferring its loot (event despawn, server cleanup, zombie fled). To hand the inventory to a killing player, use the "Zombie Killed" flow below instead.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "zombie_id": "zombie-uuid-001", "lobby_id": "lobby-uuid-789", "despawned": true }
```

Error Response (409 Conflict):

```json
{ "error": "Zombie instance not found or already despawned." }
```

#### Zombie State (Read / Update)

##### Get Zombie State

`GET /api/zombies/instances/{zombie_id}/state`
Description: Returns the zombie's current behavioural state.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "status": "chasing",
  "room_id": "exam-hall-3",
  "target_player_id": "player-uuid-123",
  "active_behaviours": [
    { "trigger": "player_nearby", "action": "chase", "range_tiles": 5 }
  ],
  "phase": "day"
}
```

##### Update Zombie State

`PATCH /api/zombies/instances/{zombie_id}/state`
Description: Updates the zombie's behavioural state (idle → chasing → stealing → hiding/dead). Called by Game Service during encounters and by the zombie tick loop.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "status": "stealing",
  "room_id": "canteen",
  "target_player_id": "player-uuid-456"
}
```

Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "status": "stealing",
  "room_id": "canteen",
  "target_player_id": "player-uuid-456",
  "active_behaviours": [
    { "trigger": "player_in_room", "action": "steal", "max_items": 2 }
  ],
  "phase": "day"
}
```

#### Zombie Stats (Read / Update)

##### Get Zombie Stats

`GET /api/zombies/instances/{zombie_id}/stats`
Description: Returns the zombie's combat-relevant stats (type, health, damage, ...).
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "type_id": "professor_zombie",
  "health": 60,
  "max_health": 100,
  "damage": 15,
  "speed": 1.2
}
```

##### Update Zombie Stats

`PATCH /api/zombies/instances/{zombie_id}/stats`
Description: Updates the zombie's current stats (e.g. damage taken during a fight). Idempotent under a per-event `event_id` where needed.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "health": 40 }
```

Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "type_id": "professor_zombie",
  "health": 40,
  "max_health": 100,
  "damage": 15,
  "speed": 1.2
}
```

#### Zombie Inventory (Read / Update)

##### Get Zombie Inventory

`GET /api/zombies/instances/{zombie_id}/inventory`
Description: Returns the zombie's full inventory of stolen resources. Called by Game Service when a zombie is killed to know what to transfer.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
[
  { "item_id": "coffee-01", "count": 1 },
  { "item_id": "metal-01", "count": 6 },
  { "item_id": "wood-01", "count": 2 }
]
```

##### Update Zombie Inventory

`PATCH /api/zombies/instances/{zombie_id}/inventory`
Description: Adds or removes stolen items from the zombie's inventory. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "operation": "add",
  "items": [
    { "item_id": "sandwich-01", "count": 1 }
  ],
  "event_id": "evt-uuid-steal-002"
}
```

Success Response (200 OK):

```json
[
  { "item_id": "coffee-01", "count": 1 },
  { "item_id": "metal-01", "count": 6 },
  { "item_id": "wood-01", "count": 2 },
  { "item_id": "sandwich-01", "count": 1 }
]
```

Error Response (409 Conflict):

```json
{ "error": "Insufficient items to remove from zombie inventory." }
```

#### Zombie Event Flows

The following endpoints reuse the primitives above for in-game event flows.

##### Zombie Steals from World

`POST /api/zombies/instances/{zombie_id}/steal-world`
Description: Records resources stolen by a zombie from a world tile. Convenience wrapper over "Update Zombie Inventory" (`operation: add`); also reserves the grabbed amount on the Resource Service map. Called by World Service when a zombie occupies a resource node. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-steal-001",
  "room_id": "lab-204",
  "items": [
    { "item_id": "metal-01", "count": 3 },
    { "item_id": "wood-01", "count": 2 }
  ]
}
```

Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "inventory": [
    { "item_id": "coffee-01", "count": 1 },
    { "item_id": "metal-01", "count": 6 },
    { "item_id": "wood-01", "count": 2 }
  ]
}
```

##### Zombie Steals from Player

`POST /api/zombies/instances/{zombie_id}/steal-player`
Description: Records resources stolen by a zombie from a player's inventory. Convenience wrapper over "Update Zombie Inventory"; also deducts the items from the Player Service inventory. Called by Game Service during encounter resolution. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-steal-002",
  "player_id": "player-uuid-123",
  "items": [{ "item_id": "sandwich-01", "count": 1 }]
}
```

Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "inventory": [
    { "item_id": "coffee-01", "count": 1 },
    { "item_id": "metal-01", "count": 6 },
    { "item_id": "wood-01", "count": 2 },
    { "item_id": "sandwich-01", "count": 1 }
  ]
}
```

##### Zombie Killed

`POST /api/zombies/instances/{zombie_id}/killed`
Description: Transfers the zombie's full inventory to the player who killed it, clears its state/stats and despawns it. Internally: reads the inventory, credits the killer via Player Service, then removes the instance. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-kill-001",
  "killer_player_id": "player-uuid-123",
  "lobby_id": "lobby-uuid-789"
}
```

Success Response (200 OK):

```json
{
  "zombie_id": "zombie-uuid-001",
  "type_id": "professor_zombie",
  "transferred_items": [
    { "item_id": "coffee-01", "count": 1 },
    { "item_id": "metal-01", "count": 6 },
    { "item_id": "wood-01", "count": 2 },
    { "item_id": "sandwich-01", "count": 1 }
  ],
  "despawned": true
}
```

Error Response (409 Conflict):

```json
{ "error": "Zombie instance not found or already despawned." }
```

---

## 4. Resource Service

Acts as the **single source of truth for resource quantities in a game session**: how much of every resource exists on the map (resource points), how much of every resource is held by any individual entity (player, zombie, base, crafting job) and an immutable audit trail of every movement as a transaction. Game Service, World Service, Base Service and Crafting Service all request resources through this service. All mutating operations are idempotent via `event_id` — resources are never duplicated and never silently disappear.

### Responsibilities

- CRUD resource points on the map (location + resource type + current amount / capacity).
- Serve the current amount of any resource on the map and update it (restock / deplete / set).
- Track and update the resource count held by any individual entity.
- Execute gathering actions: atomically deduct from a map location and credit the gathering entity, returning the appropriate amount actually gathered.
- Record every gather / transfer / allocation as a transaction and expose its details for verification.

### Endpoints

#### Resource Point CRUD

##### List Resource Points on the Map

`GET /api/resources/points`
Description: Returns all resource points on the map, optionally filtered by `room_id` or `resource`.
Headers: `Authorization: Bearer <service_jwt>`
Query Parameters: `room_id` (optional), `resource` (optional)
Success Response (200 OK):

```json
[
  {
    "point_id": "point-uuid-001",
    "room_id": "lab-204",
    "resource": "metal-01",
    "amount": 12,
    "max_amount": 30
  },
  {
    "point_id": "point-uuid-002",
    "room_id": "canteen",
    "resource": "food-01",
    "amount": 30,
    "max_amount": 50
  }
]
```

##### Create a Resource Point

`POST /api/resources/points`
Description: Creates a new resource point on the map. Called by World Service when a room / resource node is unlocked, or by moderators pre-seeding a season.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "room_id": "lab-204",
  "resource": "metal-01",
  "amount": 15,
  "max_amount": 40
}
```

Success Response (201 Created):

```json
{
  "point_id": "point-uuid-003",
  "room_id": "lab-204",
  "resource": "metal-01",
  "amount": 15,
  "max_amount": 40
}
```

##### Get a Resource Point

`GET /api/resources/points/{point_id}`
Description: Returns full details for a single resource point.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{
  "point_id": "point-uuid-001",
  "room_id": "lab-204",
  "resource": "metal-01",
  "amount": 9,
  "max_amount": 30
}
```

Error Response (404 Not Found):

```json
{ "error": "Resource point not found." }
```

##### Update a Resource Point

`PATCH /api/resources/points/{point_id}`
Description: Edits a resource point's type or capacity. Use "Update Resource Amount on the Map" below for routine restock / consume deltas.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "resource": "scrap-01", "max_amount": 60 }
```

Success Response (200 OK):

```json
{
  "point_id": "point-uuid-001",
  "room_id": "lab-204",
  "resource": "scrap-01",
  "amount": 9,
  "max_amount": 60
}
```

##### Delete a Resource Point

`DELETE /api/resources/points/{point_id}`
Description: Removes a resource point from the map (e.g. a room converted to another type). Refused while a gather transaction is in flight.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "point_id": "point-uuid-001", "deleted": true }
```

#### Resource Amount on the Map (Read / Update)

##### Get Resource Amount on the Map

`GET /api/resources/map`
Description: Returns the amount of any resource on the map. Filter by `room_id`, `resource`, or both; with no filters it returns the full map's amounts.
Headers: `Authorization: Bearer <service_jwt>`
Query Parameters: `room_id` (optional), `resource` (optional)
Success Response (200 OK):

```json
[
  { "room_id": "lab-204", "resource": "metal-01", "amount": 9 },
  { "room_id": "lab-204", "resource": "wood-01", "amount": 6 },
  { "room_id": "canteen", "resource": "food-01", "amount": 22 }
]
```

Error Response (404 Not Found):

```json
{ "error": "No such resource." }
```

##### Update Resource Amount on the Map

`PATCH /api/resources/map`
Description: Adjusts the amount of a resource at a map location: `set` (restock to a value), `add` or `remove` (delta). Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-restock-001",
  "room_id": "lab-204",
  "resource": "metal-01",
  "operation": "set",
  "amount": 20
}
```

Success Response (200 OK):

```json
{ "room_id": "lab-204", "resource": "metal-01", "amount": 20 }
```

Error Response (409 Conflict):

```json
{ "error": "Insufficient amount of metal-01 in lab-204." }
```

#### Entity Resource Count (Read / Update)

Every individual resource holder — a player, a zombie, the base or a crafting job — is addressed as an entity.

##### Get Entity Resource Count

`GET /api/resources/entities/{entity_type}/{entity_id}`
Description: Returns the current resource counts of a single entity. `entity_type` ∈ `player` | `zombie` | `base` | `crafting_job`.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
[
  { "item_id": "metal-01", "count": 3 },
  { "item_id": "wood-01", "count": 2 }
]
```

Error Response (404 Not Found):

```json
{ "error": "Entity not found." }
```

##### Update Entity Resource Count

`PATCH /api/resources/entities/{entity_type}/{entity_id}`
Description: Updates the resource counts of a single entity (`add`, `remove` or `set`). Idempotent via `event_id`; the entity stock and the transaction audit record update atomically inside `Sequel.transaction { ... }`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-credit-001",
  "operation": "add",
  "items": [
    { "item_id": "metal-01", "count": 3 }
  ]
}
```

Success Response (200 OK):

```json
[
  { "item_id": "metal-01", "count": 6 },
  { "item_id": "wood-01", "count": 2 }
]
```

Error Response (409 Conflict):

```json
{ "error": "Insufficient stock for item metal-01." }
```

#### Gather

##### Initiate a Gathering Action

`POST /api/resources/gather`
Description: When a gathering action is initiated, the service validates the resource point, deducts the gathered amount from the map and credits the gathering entity atomically. Returns the **appropriate amount actually gathered** — clamped to the amount available at the point and the entity's capacity. Idempotent via `event_id`: repeat calls with the same `event_id` return the original result without re-crediting.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-gather-001",
  "point_id": "point-uuid-001",
  "resource": "metal-01",
  "entity_type": "player",
  "entity_id": "player-uuid-123",
  "requested_amount": 10
}
```

Success Response (200 OK):

```json
{
  "gather_id": "gather-uuid-001",
  "event_id": "evt-uuid-gather-001",
  "point_id": "point-uuid-001",
  "resource": "metal-01",
  "amount_gathered": 9,
  "requested_amount": 10,
  "remaining_at_point": 0,
  "credited_to": { "entity_type": "player", "entity_id": "player-uuid-123" },
  "status": "completed"
}
```

Error Response (404 Not Found):

```json
{ "error": "Resource point not found or exhausted." }
```

#### Transactions

##### Get Transaction Details

`GET /api/resources/transactions/{transaction_id}`
Description: Returns the details of a prior gather / transfer / allocation so callers can confirm a movement completed successfully before trusting the result (transfer verification).
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{
  "transaction_id": "transaction-uuid-001",
  "event_id": "evt-uuid-gather-001",
  "type": "gather",
  "source": { "type": "point", "point_id": "point-uuid-001", "room_id": "lab-204" },
  "target": { "entity_type": "player", "entity_id": "player-uuid-123" },
  "items_transferred": [
    { "item_id": "metal-01", "count": 9 }
  ],
  "status": "completed",
  "created_at": "2026-09-05T10:05:00Z"
}
```

Error Response (404 Not Found):

```json
{ "error": "Transaction not found." }
```

---

## 5. Exam Service

Responsible for the **actual academic progression of players**: the exam catalog, the courses
that group exams together, the static question bank, live exam attempts and each player's
academic record (grades, achievements, diploma milestones).

When a player encounters a Professor Zombie, the Game Service opens an exam attempt against
this service. Questions are authored statically through the catalog endpoints below — no
dynamic generation. Passing specific exams can trigger events such as unlocking new university
areas or awarding player rewards; completing all Math exams, for example, could unlock a
"Survived the Pumpkin" achievement and notify the World Service to unlock a new wing.

### Responsibilities

- Own the exam and course catalog for the current semester.
- Own the static question bank per exam (read-only via the API — authored at exam-creation time).
- Open, track and grade exam attempts, including idempotent, replay-safe grading.
- Own each player's academic record: per-course grades, unlocked achievements, diploma progress.
- Notify the World Service (`ExamPassed`) and Player Service when a milestone is reached.

### Endpoints

#### Create an Exam

`POST /api/exams`
Description: Creates a new exam for a course. Exists so moderators can grow the exam catalog
without a redeploy, since questions are authored statically rather than generated.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{
  "course_id": "math-101",
  "title": "Calculus Final",
  "difficulty": "hard",
  "questions": [
    { "text": "2 + 2 = ?", "options": ["3", "4", "5"], "correct_answer": "4" }
  ]
}
```

Success Response (201 Created):

```json
{ "exam_id": "exam-uuid-556", "course_id": "math-101", "title": "Calculus Final", "difficulty": "hard" }
```

#### List Exams

`GET /api/exams`
Description: Lists exams for the current semester, optionally filtered by `course_id`. Exists
so the Game Service and players can browse what's available before/without an encounter.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "exam_id": "exam-uuid-555", "course_id": "math-101", "title": "Calculus Midterm", "difficulty": "medium" }]
```

#### Get Exam Details

`GET /api/exams/{exam_id}`
Description: Returns a single exam's metadata (never the question bank — see
"List Exam Questions" below for that, which is answer-key-restricted).
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "exam_id": "exam-uuid-555", "course_id": "math-101", "title": "Calculus Midterm", "difficulty": "medium" }
```

Error Response (404 Not Found):

```json
{ "error": "Exam not found." }
```

#### Update an Exam

`PATCH /api/exams/{exam_id}`
Description: Edits an exam's title/difficulty/course without recreating it, so existing
`exam-attempts` keep a valid `exam_id` to reference.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "difficulty": "medium" }
```

Success Response (200 OK):

```json
{ "exam_id": "exam-uuid-555", "course_id": "math-101", "title": "Calculus Midterm", "difficulty": "medium" }
```

#### Create a Course

`POST /api/courses`
Description: Registers a new course and the exams required to complete it. Exists because
"passed all Math exams" achievement rules need to know what "all" means for that course.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "course_id": "math-101", "name": "Calculus I", "required_exam_ids": ["exam-uuid-555", "exam-uuid-556"] }
```

Success Response (201 Created):

```json
{ "course_id": "math-101", "name": "Calculus I", "required_exam_ids": ["exam-uuid-555", "exam-uuid-556"] }
```

#### List Courses

`GET /api/courses`
Description: Lists all courses. Exists so exam-selection UIs and other services can browse the
curriculum without knowing course ids in advance.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "course_id": "math-101", "name": "Calculus I", "required_exam_count": 2 }]
```

#### Get Course Details

`GET /api/courses/{course_id}`
Description: Returns a course's full required-exam list. Exists so the Crafting Service can
check exactly which exams gate a wing/recipe unlock, not just whether the course exists.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "course_id": "math-101", "name": "Calculus I", "required_exam_ids": ["exam-uuid-555", "exam-uuid-556"] }
```

#### Update a Course

`PATCH /api/courses/{course_id}`
Description: Adds/removes required exams as the semester's curriculum changes.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "required_exam_ids": ["exam-uuid-555", "exam-uuid-556", "exam-uuid-560"] }
```

Success Response (200 OK):

```json
{ "course_id": "math-101", "name": "Calculus I", "required_exam_ids": ["exam-uuid-555", "exam-uuid-556", "exam-uuid-560"] }
```

#### List Exam Questions

`GET /api/exams/{exam_id}/questions`
Description: Returns an exam's full question bank **including the correct answer key**. Exists
for moderator/QA tooling to audit grading correctness — players never see this; they only ever
see the answer-free question list returned inline by "Start an Exam Attempt" below.
Headers: `Authorization: Bearer <service_jwt>` (internal/moderator tooling only)
Success Response (200 OK):

```json
[{ "question_id": "q-1", "text": "2 + 2 = ?", "options": ["3", "4", "5"], "correct_answer": "4" }]
```

#### Start an Exam Attempt

`POST /api/exam-attempts`
Description: Called by the Game Service when a player encounters a Professor Zombie. Exists to
turn a static exam definition into a live, timed attempt tied to one player, with the
question set stripped of correct answers.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "player_id": "player-uuid-123", "course_id": "math-101", "encounter_id": "encounter-uuid-001" }
```

Success Response (201 Created):

```json
{
  "attempt_id": "attempt-uuid-777",
  "exam_id": "exam-uuid-555",
  "questions": [{ "question_id": "q-1", "text": "2 + 2 = ?", "options": ["3", "4", "5"] }],
  "expires_at": "2026-09-08T10:10:00Z"
}
```

Error Response (409 Conflict):

```json
{ "error": "Player already has an active exam attempt." }
```

#### Submit an Answer

`POST /api/exam-attempts/{attempt_id}/answers`
Description: Records (or overwrites) the player's answer to one question as they progress
through the attempt, instead of requiring the whole exam to be submitted at once. Exists to
support autosave-as-you-go exam UIs; grading itself still happens via the final `PATCH` below.
Upserts by `question_id` — resubmitting a question overwrites the previous selection.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "question_id": "q-1", "selected": "4" }
```

Success Response (200 OK):

```json
{ "question_id": "q-1", "selected": "4" }
```

Error Response (409 Conflict):

```json
{ "error": "Cannot submit an answer after the attempt has already been graded." }
```

#### Finalize and Grade an Exam Attempt

`PATCH /api/exam-attempts/{attempt_id}`
Description: Finalizes the attempt and grades it using whichever answers were recorded via
"Submit an Answer" above. An `answers` array is optional here — pass one to submit/override
any remaining answers in the same call for clients that batch instead of autosaving.
**Idempotent** — resubmitting an already-graded `attempt_id` returns the stored result rather
than re-grading.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "answers": [{ "question_id": "q-1", "selected": "4" }] }
```

Success Response (200 OK):

```json
{ "attempt_id": "attempt-uuid-777", "score": 90, "passed": true, "achievements_unlocked": ["survived_the_pumpkin"] }
```

Error Response (410 Gone):

```json
{ "error": "Attempt expired before it was graded." }
```

#### Get Exam Attempt Answers

`GET /api/exam-attempts/{attempt_id}/answers`
Description: Returns the answers recorded for an attempt so far. Mid-attempt, this shows the
player's currently saved (ungraded) selections, e.g. to resume after a reconnect; after
grading, each answer is additionally annotated with whether it was correct, so a player can
review their mistakes or a moderator can audit a disputed grade — without re-exposing the full
answer key the way "List Exam Questions" does.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "question_id": "q-1", "selected": "4", "correct": true }]
```

Error Response (404 Not Found):

```json
{ "error": "No answers submitted yet for this attempt." }
```

#### Get Exam Attempt Status

`GET /api/exam-attempts/{attempt_id}`
Description: Returns an attempt's current state. Exists so a player's client can resume/poll
("how much time is left, has this already been graded?") after a reconnect.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "attempt_id": "attempt-uuid-777", "exam_id": "exam-uuid-555", "status": "in_progress", "expires_at": "2026-09-08T10:10:00Z" }
```

#### Submit an Answer

`POST /api/exam-attempts/{attempt_id}/answers`
Description: Records (or overwrites) the player's answer to one question as they progress
through the attempt, instead of requiring the whole exam to be submitted at once. Exists to
support autosave-as-you-go exam UIs; grading itself still happens via the final `PATCH` below.
Upserts by `question_id` — resubmitting a question overwrites the previous selection.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "question_id": "q-1", "selected": "4" }
```

Success Response (200 OK):

```json
{ "question_id": "q-1", "selected": "4" }
```

Error Response (409 Conflict):

```json
{ "error": "Cannot submit an answer after the attempt has already been graded." }
```

#### Finalize and Grade an Exam Attempt

`PATCH /api/exam-attempts/{attempt_id}`
Description: Finalizes the attempt and grades it using whichever answers were recorded via
"Submit an Answer" above. An `answers` array is optional here — pass one to submit/override
any remaining answers in the same call for clients that batch instead of autosaving.
**Idempotent** — resubmitting an already-graded `attempt_id` returns the stored result rather
than re-grading.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "answers": [{ "question_id": "q-1", "selected": "4" }] }
```

Success Response (200 OK):

```json
{ "attempt_id": "attempt-uuid-777", "score": 90, "passed": true, "achievements_unlocked": ["survived_the_pumpkin"] }
```

Error Response (410 Gone):

```json
{ "error": "Attempt expired before it was graded." }
```

#### Get Exam Attempt Answers

`GET /api/exam-attempts/{attempt_id}/answers`
Description: Returns the answers recorded for an attempt so far. Mid-attempt, this shows the
player's currently saved (ungraded) selections, e.g. to resume after a reconnect; after
grading, each answer is additionally annotated with whether it was correct, so a player can
review their mistakes or a moderator can audit a disputed grade — without re-exposing the full
answer key the way "List Exam Questions" does.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "question_id": "q-1", "selected": "4", "correct": true }]
```

Error Response (404 Not Found):

```json
{ "error": "No answers submitted yet for this attempt." }
```

#### Cancel an Exam Attempt

`DELETE /api/exam-attempts/{attempt_id}`
Description: Called by the Game Service if a player flees the encounter before finishing.
Exists to free the player up to start a new attempt instead of waiting out the `409 Conflict`
from "Start an Exam Attempt" until natural expiry.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "attempt_id": "attempt-uuid-777", "status": "cancelled" }
```

Error Response (409 Conflict):

```json
{ "error": "Cannot cancel an attempt that has already been graded." }
```

#### Get Player Exam History

`GET /api/players/{player_id}/exams`
Description: Lightweight per-course grade list. Exists as the narrow read the Crafting Service
already depends on to gate exam-locked recipes, without pulling the full academic record below.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "exam_id": "exam-uuid-555", "course_id": "math-101", "status": "passed", "grade": 90 }]
```

#### Get Player Achievements

`GET /api/players/{player_id}/achievements`
Description: Lightweight unlocked-achievements list, for the same "narrow read" reason as above.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "achievement_id": "survived_the_pumpkin", "unlocked_at": "2026-09-08T10:09:00Z" }]
```

#### Create a Player Academic Record

`POST /api/players/{player_id}/academic-record`
Description: Bootstraps a player's academic record (grades + achievements + diploma progress
counters). Exists so Player Service (or an admin tool) can pre-provision it at season start;
in normal play it's auto-created the first time an attempt is graded.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{}
```

Success Response (201 Created):

```json
{ "player_id": "player-uuid-123", "grades": [], "achievements": [], "diploma_progress": { "courses_completed": 0, "courses_required": 5 } }
```

#### Get Player Academic Record

`GET /api/players/{player_id}/academic-record`
Description: Returns grades, achievements and diploma progress together. Exists for a
player's diploma/profile screen, which needs all three at once instead of three round-trips.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{
  "player_id": "player-uuid-123",
  "grades": [{ "course_id": "math-101", "grade": 90 }],
  "achievements": [{ "achievement_id": "survived_the_pumpkin", "unlocked_at": "2026-09-08T10:09:00Z" }],
  "diploma_progress": { "courses_completed": 1, "courses_required": 5 }
}
```
---

## 6. World Service

Creates and owns the **persistent physical state of the university**: the overall campus map,
its zones, rooms and their coordinates, resource node placement, barricade levels and zombie
spawn point configuration.

Different room types provide different resources — Laboratory → metal scraps, Library → paper,
Canteen → food, Classrooms → textbooks. The map expands when the Exam Service reports an
`ExamPassed` event, procedurally unlocking a new section.

### Responsibilities

- Own the campus `map` and its `zones`, including locked/unlocked state.
- Own `rooms`, their `coordinates` on the map, type and current barricade level.
- Own `resource-nodes` placement per room (not their gathered quantities — Resource Service owns that).
- Own `zombie-spawns` configuration per room.
- Procedurally generate/unlock a new section when notified of an `ExamPassed` event.
- Reflect barricade-level changes reported by the Base Service.

### Endpoints

#### Create the Campus Map

`POST /api/world/map`
Description: Bootstraps the campus map for a new game world, seeding its first zone. Exists so
a fresh lobby/season starts from a known, minimal campus rather than an empty database.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "name": "FAF Campus", "initial_zone": { "zone_id": "zone-cab", "name": "FAF Cab" } }
```

Success Response (201 Created):

```json
{ "map_id": "map-uuid-001", "name": "FAF Campus", "version": 1, "zones": ["zone-cab"] }
```

#### Get the Campus Map

`GET /api/world/map`
Description: Returns the full current map graph in one call. Exists for map-rendering clients
and debugging tools that would otherwise need to stitch zones/rooms together themselves.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "map_id": "map-uuid-001", "name": "FAF Campus", "version": 3, "zones": ["zone-cab", "zone-math-wing"] }
```

#### Update the Campus Map

`PATCH /api/world/map`
Description: Edits global map metadata (name/version label) without touching individual
zones/rooms.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "name": "FAF Campus — Autumn 2026" }
```

Success Response (200 OK):

```json
{ "map_id": "map-uuid-001", "name": "FAF Campus — Autumn 2026", "version": 4, "zones": ["zone-cab", "zone-math-wing"] }
```

#### Set Room Coordinates

`POST /api/world/rooms/{room_id}/coordinates`
Description: Places a room on the map grid. Exists so a room has a renderable position the
moment it's created, separately from its game-state fields (type, barricade level).
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "x": 4, "y": 2 }
```

Success Response (201 Created):

```json
{ "room_id": "lab-a1", "x": 4, "y": 2 }
```

#### Get Room Coordinates

`GET /api/world/rooms/{room_id}/coordinates`
Description: Returns a room's grid position. Exists for the Game Service's map client to
render rooms in the right place.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "room_id": "lab-a1", "x": 4, "y": 2 }
```

#### Update Room Coordinates

`PATCH /api/world/rooms/{room_id}/coordinates`
Description: Repositions a room after a procedural layout adjustment.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "x": 5, "y": 2 }
```

Success Response (200 OK):

```json
{ "room_id": "lab-a1", "x": 5, "y": 2 }
```

#### Remove Room Coordinates *(proposed — confirm before implementing)*

`DELETE /api/world/rooms/{room_id}/coordinates`
Description: Unplaces a room from the visible map grid without deleting the room record
itself (e.g. temporarily hiding a room during an event). Marked uncertain because it's not
clear yet whether a room should ever exist without a position — confirm during design review.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Success Response (200 OK):

```json
{ "room_id": "lab-a1", "removed": true }
```

#### Create a Zombie Spawn Point

`POST /api/world/zombie-spawns`
Description: Registers a new spawn point in a room, e.g. when a zone is procedurally unlocked.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "room_id": "exam-hall-3", "zombie_type": "professor_zombie" }
```

Success Response (201 Created):

```json
{ "spawn_id": "spawn-1", "room_id": "exam-hall-3", "zombie_type": "professor_zombie" }
```

#### List Zombie Spawn Points

`GET /api/world/zombie-spawns`
Description: Used by the Game Service to determine which entities can appear during a cycle,
optionally filtered by `?zone_id=`.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
[{ "spawn_id": "spawn-1", "room_id": "exam-hall-3", "zombie_type": "professor_zombie" }]
```

#### Update a Zombie Spawn Point

`PATCH /api/world/zombie-spawns/{spawn_id}`
Description: Rebalances which zombie type spawns at a point without deleting and recreating it.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "zombie_type": "tourist_zombie" }
```

Success Response (200 OK):

```json
{ "spawn_id": "spawn-1", "room_id": "exam-hall-3", "zombie_type": "tourist_zombie" }
```

#### Create a Resource Node

`POST /api/world/rooms/{room_id}/resource-nodes`
Description: Places a new resource node in a room, e.g. seeding a Laboratory's metal-scrap
node when the room is created. World Service owns *that a node exists here*; Resource Service
owns the economy-wide quantities it produces.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "resource": "metal_scraps", "initial_yield": 50 }
```

Success Response (201 Created):

```json
{ "node_id": "node-2", "room_id": "lab-a1", "resource": "metal_scraps", "remaining": 50 }
```

#### List Resource Nodes in a Room

`GET /api/world/rooms/{room_id}/resource-nodes`
Description: Used by Resource Service to see what nodes exist in a room before validating a
gathering action against them.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
[{ "node_id": "node-1", "room_id": "lab-a1", "resource": "metal_scraps", "remaining": 40 }]
```

#### Update a Resource Node

`PATCH /api/world/rooms/{room_id}/resource-nodes/{node_id}`
Description: Called by Resource Service after it applies a gathering action, to reflect the
node's new remaining yield (or by a moderator to restock it).
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "remaining": 35 }
```

Success Response (200 OK):

```json
{ "node_id": "node-1", "room_id": "lab-a1", "resource": "metal_scraps", "remaining": 35 }
```

#### Remove a Resource Node *(proposed, analyse further before implementing)*

`DELETE /api/world/rooms/{room_id}/resource-nodes/{node_id}`
Description: Removes a node entirely if a room's layout changes (e.g. Laboratory converted to
Classroom). Marked uncertain — confirm with the team whether nodes should ever be deleted
versus just left at `remaining: 0`.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Success Response (200 OK):

```json
{ "node_id": "node-1", "removed": true }
```

#### Create a Room

`POST /api/world/rooms`
Description: Adds a new room to a zone. Used internally during `ExamPassed` procedural
generation and available for manual admin use.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "zone_id": "zone-math-wing", "type": "classroom" }
```

Success Response (201 Created):

```json
{ "room_id": "classroom-b2", "zone_id": "zone-math-wing", "type": "classroom", "resource": "textbooks", "barricade_level": 0 }
```

#### List Rooms

`GET /api/world/rooms`
Description: Used by the Game Service to fetch available rooms and their state, optionally
filtered by `?zone_id=`.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "room_id": "lab-a1", "zone_id": "zone-cab", "type": "laboratory", "resource": "metal_scraps", "barricade_level": 1 }]
```

#### Get Room Details

`GET /api/world/rooms/{room_id}`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{
  "room_id": "lab-a1",
  "zone_id": "zone-cab",
  "type": "laboratory",
  "resource_nodes": [{ "node_id": "node-1", "resource": "metal_scraps", "remaining": 40 }],
  "barricade_level": 1
}
```

#### Update a Room

`PATCH /api/world/rooms/{room_id}`
Description: Edits a room's type/resource. For barricade-level changes specifically, use
"Update Room Barricade Level" below instead — it's the idempotent, service-to-service path.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "type": "library", "resource": "paper" }
```

Success Response (200 OK):

```json
{ "room_id": "lab-a1", "zone_id": "zone-cab", "type": "library", "resource": "paper", "barricade_level": 1 }
```

#### Update Room Barricade Level

`POST /api/world/rooms/{room_id}/barricades`
Description: Called by the Base Service after players spend resources reinforcing a room.
**Idempotent** via `event_id` — kept as a dedicated action endpoint (not folded into the
generic `PATCH` above) because it's driven by an external event, not an admin edit.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "event_id": "evt-uuid-321", "barricade_level": 2 }
```

Success Response (200 OK):

```json
{ "room_id": "lab-a1", "barricade_level": 2 }
```

#### Create a Zone

`POST /api/world/zones`
Description: Registers a new zone/wing. Used internally by "Unlock New Section" below and
available for admins pre-seeding the initial campus.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "name": "Math Wing", "unlocked": false }
```

Success Response (201 Created):

```json
{ "zone_id": "zone-math-wing", "name": "Math Wing", "unlocked": false }
```

#### List Zones

`GET /api/world/zones`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "zone_id": "zone-cab", "name": "FAF Cab", "unlocked": true }, { "zone_id": "zone-math-wing", "name": "Math Wing", "unlocked": false }]
```

#### Get Zone Details

`GET /api/world/zones/{zone_id}`
Description: Exists so the Crafting Service can check one specific zone's unlock state by id,
instead of filtering the full zone list, when gating a wing-locked recipe.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "zone_id": "zone-math-wing", "name": "Math Wing", "unlocked": false }
```

#### Update a Zone

`PATCH /api/world/zones/{zone_id}`
Description: Manual edits (e.g. renaming a wing). The actual lock→unlock *transition* keeps
its own dedicated action endpoint below since it's event-sourced from the Exam Service.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "name": "Renovated Math Wing" }
```

Success Response (200 OK):

```json
{ "zone_id": "zone-math-wing", "name": "Renovated Math Wing", "unlocked": false }
```

#### Unlock New Section (Exam Passed)

`POST /api/world/unlocks`
Description: Called by the Exam Service when a player passes a milestone exam (`ExamPassed`).
Procedurally generates/unlocks a new zone (and its rooms, coordinates and resource nodes).
**Idempotent** via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "event_id": "evt-uuid-555", "player_id": "player-uuid-123", "course_id": "math-101", "achievement_id": "survived_the_pumpkin" }
```

Success Response (200 OK):

```json
{ "zone_id": "zone-math-wing", "unlocked": true, "rooms_added": ["classroom-b2", "lib-b1"] }
```

---
## 7. Base Service

Responsible for the **player's survival base**, initially the FAF Cab room. Owns what the players have **built or changed** inside the campus geography: annexed rooms, barricades, facilities, storage capacity, decorations and Kiki. World Service owns the geography itself; Resource Service owns the stock that gets spent. Every mutating call is idempotent via `event_id` and runs inside a single `Sequel.transaction { ... }` together with the Resource Service consume call.

### Responsibilities

- CRUD the base of a lobby (one base per session, seeded with the FAF Cab room).
- Annex rooms into the base and track their reinforcement state.
- Build, repair, reinforce and destroy barricades; report the new level to World Service.
- Build and upgrade facilities (workbench, coffee machine, infirmary, watchtower, ...).
- Unlock storage capacity and track decorations of the homeroom.
- Own the upgrade catalog with resource costs and unlock requirements.
- Handle Kiki interactions: consume food via Resource Service, grant a random booster via Player Service.
- Spend resources only through Resource Service (`PATCH /api/resources/entities/base/{base_id}` with `operation: remove`).

### Dependencies

| Calls | Endpoint | Why |
| --- | --- | --- |
| Resource Service | `PATCH /api/resources/entities/base/{base_id}` (`remove`) | spend wood / metal / food for any build, upgrade or feeding |
| Resource Service | `GET /api/resources/entities/base/{base_id}` | pre-check stock before starting a build |
| World Service | `POST /api/world/rooms/{room_id}/barricades` | reflect the new barricade level on the map |
| World Service | `GET /api/world/rooms/{room_id}` | verify a room exists and is cleared before annexing it |
| Player Service | `POST /api/players/{player_id}/inventory` | put Kiki's booster into the player's inventory |
| Player Service | `GET /api/players/{player_id}/xp` | level-gated upgrades |

Called by: Game Service (result of `barricade` / `repair` / `upgrade` timed actions, zombie damage on barricades), clients (base screen, Kiki).

### Endpoints

#### Base CRUD

##### Create a Base

`POST /api/bases`
Description: Creates the base for a session. Called by Game Service when a lobby starts. The homeroom (FAF Cab) is annexed automatically. Idempotent via `event_id` — a second call for the same lobby returns the existing base.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-base-001",
  "lobby_id": "lobby-uuid-789",
  "map_id": "map-uuid-001",
  "home_room_id": "faf-cab"
}
```

Success Response (201 Created):

```json
{
  "base_id": "base-uuid-001",
  "lobby_id": "lobby-uuid-789",
  "name": "FAF Cab",
  "level": 1,
  "rooms": [{ "room_id": "faf-cab", "reinforced": false, "barricade_level": 0 }],
  "facilities": [],
  "storage": { "capacity": 50, "used": 0 }
}
```

Error Response (409 Conflict):

```json
{ "error": "Lobby already has a base." }
```

##### Get a Base

`GET /api/bases/{base_id}`
Description: Full snapshot of the base — rooms, barricades, facilities, storage, decorations. Used by the base screen and by Game Service on reconnect.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{
  "base_id": "base-uuid-001",
  "lobby_id": "lobby-uuid-789",
  "name": "FAF Cab",
  "level": 2,
  "rooms": [
    { "room_id": "faf-cab", "reinforced": true, "barricade_level": 2 },
    { "room_id": "lab-a1", "reinforced": false, "barricade_level": 1 }
  ],
  "facilities": [{ "facility_id": "fac-uuid-1", "type": "workbench", "level": 1 }],
  "storage": { "capacity": 80, "used": 31 },
  "decorations": [{ "decoration_id": "dec-uuid-1", "item_id": "poster-01", "slot": "wall_left" }]
}
```

Error Response (404 Not Found):

```json
{ "error": "Base not found." }
```

##### Get Base by Lobby

`GET /api/bases?lobby_id={lobby_id}`
Description: Resolves the base of a session. Game Service uses it because it only knows the `lobby_id`.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK): same shape as "Get a Base".

##### Update a Base

`PATCH /api/bases/{base_id}`
Description: Renames the base or bumps its level after an upgrade. Structural changes (rooms, barricades, facilities) use the dedicated endpoints below.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "name": "Fortress Cab" }
```

Success Response (200 OK):

```json
{ "base_id": "base-uuid-001", "name": "Fortress Cab", "level": 2 }
```

##### Delete a Base

`DELETE /api/bases/{base_id}`
Description: Removes the base when the session ends. Called by Game Service.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "base_id": "base-uuid-001", "deleted": true }
```

#### Rooms

##### List Base Rooms

`GET /api/bases/{base_id}/rooms`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  { "room_id": "faf-cab", "reinforced": true, "barricade_level": 2, "annexed_at": "2026-09-10T14:00:00Z" },
  { "room_id": "lab-a1", "reinforced": false, "barricade_level": 1, "annexed_at": "2026-09-10T14:20:00Z" }
]
```

##### Annex a Room

`POST /api/bases/{base_id}/rooms`
Description: Expands the base into a cleared room. Verifies the room via World Service, charges the expansion cost via Resource Service, then adds the room — all atomically. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-annex-001",
  "room_id": "lab-a1",
  "player_id": "player-uuid-123"
}
```

Success Response (201 Created):

```json
{
  "room_id": "lab-a1",
  "reinforced": false,
  "barricade_level": 0,
  "cost_paid": [{ "item_id": "wood-01", "count": 10 }]
}
```

Error Response (409 Conflict):

```json
{ "error": "Room is not cleared or is already part of the base." }
```

Error Response (402 Payment Required):

```json
{ "error": "Insufficient resources.", "missing": [{ "item_id": "wood-01", "count": 4 }] }
```

##### Remove a Room from the Base

`DELETE /api/bases/{base_id}/rooms/{room_id}`
Description: Room lost to zombies or abandoned. The homeroom cannot be removed.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "room_id": "lab-a1", "removed": true }
```

#### Barricades

##### Get Barricade

`GET /api/bases/{base_id}/rooms/{room_id}/barricade`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "room_id": "lab-a1", "barricade_level": 1, "hp": 60, "max_hp": 100 }
```

##### Build / Reinforce a Barricade

`POST /api/bases/{base_id}/rooms/{room_id}/barricade`
Description: Builds a new barricade or raises its level. Result of the Game Service `barricade` timed action. Consumes wood + metal via Resource Service (or a `BARRICADE_KIT` item via Player Service when `kit_item_id` is given), then reports the new level to World Service. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-barricade-001",
  "player_id": "player-uuid-123",
  "kit_item_id": null
}
```

Success Response (200 OK):

```json
{
  "room_id": "lab-a1",
  "barricade_level": 2,
  "hp": 150,
  "max_hp": 150,
  "cost_paid": [{ "item_id": "wood-01", "count": 5 }, { "item_id": "metal-01", "count": 2 }]
}
```

Error Response (402 Payment Required):

```json
{ "error": "Insufficient resources.", "missing": [{ "item_id": "metal-01", "count": 2 }] }
```

##### Repair / Damage a Barricade

`PATCH /api/bases/{base_id}/rooms/{room_id}/barricade`
Description: `repair` restores HP and costs wood (Game Service `repair` action); `damage` lowers HP after a zombie attack (Game Service, no cost). Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-attack-042",
  "operation": "damage",
  "amount": 30
}
```

Success Response (200 OK):

```json
{ "room_id": "lab-a1", "barricade_level": 2, "hp": 120, "max_hp": 150, "destroyed": false }
```

##### Destroy a Barricade

`DELETE /api/bases/{base_id}/rooms/{room_id}/barricade`
Description: Barricade broken through. Level resets to 0 and World Service is notified.
Headers: `Authorization: Bearer <service_jwt>`
Success Response (200 OK):

```json
{ "room_id": "lab-a1", "barricade_level": 0, "destroyed": true }
```

#### Facilities

##### List Facilities

`GET /api/bases/{base_id}/facilities`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  { "facility_id": "fac-uuid-1", "type": "workbench", "level": 1, "room_id": "faf-cab" },
  { "facility_id": "fac-uuid-2", "type": "coffee_machine", "level": 2, "room_id": "faf-cab" }
]
```

##### Build a Facility

`POST /api/bases/{base_id}/facilities`
Description: Builds a facility from the upgrade catalog. Checks requirements (base level, player level, unlocked exam), consumes the cost via Resource Service. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-facility-001",
  "player_id": "player-uuid-123",
  "upgrade_id": "workbench",
  "room_id": "faf-cab"
}
```

Success Response (201 Created):

```json
{
  "facility_id": "fac-uuid-1",
  "type": "workbench",
  "level": 1,
  "room_id": "faf-cab",
  "cost_paid": [{ "item_id": "wood-01", "count": 8 }, { "item_id": "metal-01", "count": 4 }]
}
```

Error Response (409 Conflict):

```json
{ "error": "Requirement not met: base level 2 required." }
```

##### Get a Facility

`GET /api/bases/{base_id}/facilities/{facility_id}`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "facility_id": "fac-uuid-1", "type": "workbench", "level": 1, "room_id": "faf-cab", "next_upgrade_cost": [{ "item_id": "metal-01", "count": 6 }] }
```

##### Upgrade a Facility

`PATCH /api/bases/{base_id}/facilities/{facility_id}`
Description: Raises the facility one level. Consumes the next-level cost. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "event_id": "evt-uuid-facility-upg-001", "player_id": "player-uuid-123" }
```

Success Response (200 OK):

```json
{ "facility_id": "fac-uuid-1", "type": "workbench", "level": 2, "cost_paid": [{ "item_id": "metal-01", "count": 6 }] }
```

##### Demolish a Facility

`DELETE /api/bases/{base_id}/facilities/{facility_id}`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "facility_id": "fac-uuid-1", "deleted": true }
```

#### Storage

##### Get Storage

`GET /api/bases/{base_id}/storage`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "capacity": 80, "used": 31, "next_upgrade_cost": [{ "item_id": "wood-01", "count": 12 }] }
```

##### Unlock Storage Capacity

`PATCH /api/bases/{base_id}/storage`
Description: Raises capacity by one tier. Consumes the cost. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "event_id": "evt-uuid-storage-001", "player_id": "player-uuid-123" }
```

Success Response (200 OK):

```json
{ "capacity": 120, "used": 31 }
```

#### Decorations

##### List Decorations

`GET /api/bases/{base_id}/decorations`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[{ "decoration_id": "dec-uuid-1", "item_id": "poster-01", "slot": "wall_left", "placed_by": "player-uuid-123" }]
```

##### Place a Decoration

`POST /api/bases/{base_id}/decorations`
Description: Places a cosmetic item from the player's inventory into a homeroom slot. The item is consumed via Player Service (`delta: -1`).
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "operation_id": "dec-op-uuid-1", "player_id": "player-uuid-123", "item_id": "poster-01", "slot": "wall_left" }
```

Success Response (201 Created):

```json
{ "decoration_id": "dec-uuid-1", "item_id": "poster-01", "slot": "wall_left" }
```

Error Response (409 Conflict):

```json
{ "error": "Slot already occupied." }
```

##### Remove a Decoration

`DELETE /api/bases/{base_id}/decorations/{decoration_id}`
Description: Removes the decoration and returns the item to the player's inventory.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "decoration_id": "dec-uuid-1", "removed": true }
```

#### Kiki

##### Feed Kiki

`POST /api/bases/{base_id}/kiki/feed`
Description: The player feeds Kiki food from the base stock. Food is consumed via Resource Service, then a random reward is rolled (booster item, XP, nothing). Items are granted through Player Service, XP through `PATCH /api/players/{player_id}/xp`. Idempotent via `event_id` — the same roll is returned on retry.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{ "event_id": "evt-uuid-kiki-001", "player_id": "player-uuid-123", "food": [{ "item_id": "food-01", "count": 2 }] }
```

Success Response (200 OK):

```json
{
  "reward": { "type": "item", "code": "ENERGY_BOOSTER", "quantity": 1 },
  "kiki_mood": "happy",
  "cooldown_until": "2026-09-10T15:00:00Z"
}
```

Error Response (429 Too Many Requests):

```json
{ "error": "Kiki is full. Try again later." }
```

##### Get Kiki Status

`GET /api/bases/{base_id}/kiki`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "kiki_mood": "hungry", "times_fed_today": 1, "cooldown_until": null }
```

#### Upgrade Catalog

##### List Upgrades

`GET /api/bases/upgrades`
Description: All facilities, storage tiers and expansion costs, with their requirements. `?base_id=` annotates each entry with `available: true|false` for that base.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  {
    "upgrade_id": "workbench",
    "category": "facility",
    "name": "Workbench",
    "cost": [{ "item_id": "wood-01", "count": 8 }, { "item_id": "metal-01", "count": 4 }],
    "requires": { "base_level": 1 },
    "effect": "unlocks crafting at the base"
  },
  {
    "upgrade_id": "storage_t2",
    "category": "storage",
    "name": "Storage tier 2",
    "cost": [{ "item_id": "wood-01", "count": 12 }],
    "requires": { "base_level": 2 },
    "effect": "+40 capacity"
  }
]
```

##### Create an Upgrade

`POST /api/bases/upgrades`
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "upgrade_id": "watchtower", "category": "facility", "name": "Watchtower", "cost": [{ "item_id": "wood-01", "count": 20 }], "requires": { "base_level": 3 }, "effect": "reveals zombies one room away" }
```

Success Response (201 Created): the created upgrade.

##### Update / Delete an Upgrade

`PATCH /api/bases/upgrades/{upgrade_id}` · `DELETE /api/bases/upgrades/{upgrade_id}`
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Description: Rebalance costs / requirements, or retire an upgrade (refused while a base still has it built).

## 8. Crafting Service

Allows players to **combine resources into survival equipment**. Owns the recipe catalog, its unlock rules and every crafting job. Materials are validated and consumed through Resource Service, the crafted object is transferred to the player's inventory through Player Service; both happen inside one `Sequel.transaction { ... }` with a compensating refund if the credit fails. All mutating calls are idempotent via `event_id`.

Example recipes: Wood + Metal → Barricade Kit · Paper + Metal → Improvised Weapon · Food + Chemicals → Energy Booster · Metal + Electronics → Zombie Detector · Paper + Wood → Exam Cheat Sheet.

### Responsibilities

- CRUD recipes: inputs, output item, crafting time, unlock requirements.
- Resolve which recipes a player can currently craft (exam passed, level reached, wing unlocked, special resource discovered).
- Execute crafting jobs atomically: validate materials → consume via Resource Service → credit item via Player Service.
- Track job history so duplicated completion events cannot craft twice.
- Receive unlock notifications from Exam Service (`ExamPassed`) and World Service (wing unlocked).

### Dependencies

| Calls | Endpoint | Why |
| --- | --- | --- |
| Resource Service | `GET /api/resources/entities/player/{player_id}` | validate the player has the materials |
| Resource Service | `PATCH /api/resources/entities/player/{player_id}` (`remove`) | consume the materials |
| Player Service | `POST /api/players/{player_id}/inventory` | credit the crafted object |
| Player Service | `GET /api/players/{player_id}/xp` | level-gated recipes |
| Exam Service | `GET /api/players/{player_id}/exams` | exam-gated recipes |
| World Service | `GET /api/world/zones/{zone_id}` | wing-gated recipes |

Called by: clients (crafting screen), Game Service (craft as a timed action), Exam Service / World Service (unlock notifications).

### Endpoints

#### Recipe CRUD

##### Create a Recipe

`POST /api/crafting/recipes`
Description: Defines a recipe. `requires` is optional; an absent field means no gate.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{
  "recipe_id": "barricade_kit",
  "name": "Barricade Kit",
  "inputs": [{ "item_id": "wood-01", "count": 3 }, { "item_id": "metal-01", "count": 1 }],
  "output": { "code": "BARRICADE_KIT", "type": "equipment", "quantity": 1 },
  "craft_time_seconds": 60,
  "requires": { "exam_id": null, "player_level": 1, "zone_id": null, "facility": "workbench" }
}
```

Success Response (201 Created):

```json
{
  "recipe_id": "barricade_kit",
  "name": "Barricade Kit",
  "inputs": [{ "item_id": "wood-01", "count": 3 }, { "item_id": "metal-01", "count": 1 }],
  "output": { "code": "BARRICADE_KIT", "type": "equipment", "quantity": 1 },
  "craft_time_seconds": 60,
  "requires": { "player_level": 1, "facility": "workbench" }
}
```

Error Response (409 Conflict):

```json
{ "error": "Recipe id already exists." }
```

##### List Recipes

`GET /api/crafting/recipes`
Description: Full catalog. `?player_id=` annotates each recipe with `available` and `locked_by`.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  { "recipe_id": "barricade_kit", "name": "Barricade Kit", "available": true, "locked_by": [] },
  { "recipe_id": "exam_cheat_sheet", "name": "Exam Cheat Sheet", "available": false, "locked_by": ["exam:math-101"] }
]
```

##### Get a Recipe

`GET /api/crafting/recipes/{recipe_id}`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{
  "recipe_id": "zombie_detector",
  "name": "Zombie Detector",
  "inputs": [{ "item_id": "metal-01", "count": 4 }, { "item_id": "electronics-01", "count": 2 }],
  "output": { "code": "ZOMBIE_DETECTOR", "type": "equipment", "quantity": 1 },
  "craft_time_seconds": 180,
  "requires": { "zone_id": "zone-lab-wing", "player_level": 5 }
}
```

Error Response (404 Not Found):

```json
{ "error": "Recipe not found." }
```

##### Update a Recipe

`PATCH /api/crafting/recipes/{recipe_id}`
Description: Rebalances inputs, time or requirements without changing the id, so existing jobs keep a valid reference.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Payload:

```json
{ "inputs": [{ "item_id": "wood-01", "count": 2 }, { "item_id": "metal-01", "count": 1 }], "craft_time_seconds": 45 }
```

Success Response (200 OK): the updated recipe.

##### Delete a Recipe

`DELETE /api/crafting/recipes/{recipe_id}`
Description: Retires a recipe. Refused while jobs for it are in progress.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Success Response (200 OK):

```json
{ "recipe_id": "exam_cheat_sheet", "deleted": true }
```

Error Response (409 Conflict):

```json
{ "error": "Recipe has jobs in progress." }
```

#### Player Recipes & Unlocks

##### List Available Recipes for a Player

`GET /api/crafting/players/{player_id}/recipes`
Description: Only recipes the player can craft right now, with the materials still missing.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  { "recipe_id": "barricade_kit", "name": "Barricade Kit", "can_craft_now": true, "missing": [] },
  { "recipe_id": "improvised_weapon", "name": "Improvised Weapon", "can_craft_now": false, "missing": [{ "item_id": "paper-01", "count": 2 }] }
]
```

##### Get Player Unlocks

`GET /api/crafting/players/{player_id}/unlocks`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
[
  { "recipe_id": "exam_cheat_sheet", "unlocked_by": "exam:math-101", "unlocked_at": "2026-09-08T10:09:00Z" }
]
```

##### Unlock Recipes **[internal]**

`POST /api/crafting/players/{player_id}/unlocks`
Description: Called by Exam Service on `ExamPassed` and by World Service when a wing is unlocked. Resolves every recipe whose `requires` is satisfied by the event and marks it unlocked. Idempotent via `event_id`.
Headers: `Authorization: Bearer <service_jwt>`
Payload:

```json
{ "event_id": "evt-uuid-555", "source": "exam-service", "exam_id": "exam-uuid-555", "course_id": "math-101" }
```

Success Response (200 OK):

```json
{ "unlocked": ["exam_cheat_sheet"] }
```

##### Revoke an Unlock

`DELETE /api/crafting/players/{player_id}/unlocks/{recipe_id}`
Description: Moderator correction.
Headers: `Authorization: Bearer <jwt>` (`moderator` role required)
Success Response (200 OK):

```json
{ "recipe_id": "exam_cheat_sheet", "revoked": true }
```

#### Crafting Jobs

##### Craft an Item (Create Job)

`POST /api/crafting/jobs`
Description: Executes a recipe for a player. Steps, all inside one transaction: check unlock → check materials (Resource Service) → consume materials (`remove`) → credit the output (Player Service `POST /inventory`) → store the job. If the credit fails, materials are refunded (`add`) and the job is stored as `failed`. Idempotent via `event_id`: a retry returns the stored job without crafting again. When called by Game Service as a timed action, the job stays `in_progress` until `completes_at` and the credit happens on completion.
Headers: `Authorization: Bearer <jwt>`
Payload:

```json
{
  "event_id": "evt-uuid-craft-001",
  "player_id": "player-uuid-123",
  "recipe_id": "barricade_kit",
  "quantity": 1,
  "base_id": "base-uuid-001"
}
```

Success Response (201 Created):

```json
{
  "job_id": "job-uuid-001",
  "player_id": "player-uuid-123",
  "recipe_id": "barricade_kit",
  "quantity": 1,
  "status": "completed",
  "consumed": [{ "item_id": "wood-01", "count": 3 }, { "item_id": "metal-01", "count": 1 }],
  "produced": { "item_id": "item-uuid-12", "code": "BARRICADE_KIT", "quantity": 1 },
  "completes_at": "2026-09-10T14:01:00Z"
}
```

Error Response (403 Forbidden):

```json
{ "error": "Recipe locked.", "locked_by": ["exam:math-101"] }
```

Error Response (402 Payment Required):

```json
{ "error": "Insufficient materials.", "missing": [{ "item_id": "metal-01", "count": 1 }] }
```

Error Response (409 Conflict):

```json
{ "error": "Facility 'workbench' not built in base." }
```

##### Get a Job

`GET /api/crafting/jobs/{job_id}`
Description: Polling fallback and audit of a past craft.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK): same shape as "Craft an Item".

Error Response (404 Not Found):

```json
{ "error": "Job not found." }
```

##### List Jobs

`GET /api/crafting/jobs?player_id={player_id}&status=completed`
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "jobs": [{ "job_id": "job-uuid-001", "recipe_id": "barricade_kit", "status": "completed", "created_at": "2026-09-10T14:00:00Z" }] }
```

##### Cancel a Job

`DELETE /api/crafting/jobs/{job_id}`
Description: Cancels an `in_progress` timed craft and refunds the materials. Completed jobs cannot be cancelled.
Headers: `Authorization: Bearer <jwt>`
Success Response (200 OK):

```json
{ "job_id": "job-uuid-001", "status": "cancelled", "refunded": [{ "item_id": "wood-01", "count": 3 }, { "item_id": "metal-01", "count": 1 }] }
```

Error Response (409 Conflict):

```json
{ "error": "Job already completed." }
```

---

## Branch Structure

### Main Branches

- `main`  Production-ready code, always deployable
- `dev` Integration branch for features, staging environment

### Branch Protection Rules

- Approvals Required: 1 reviewer minimum ( not him self )  
- Dismiss Stale Reviews: Enabled (reviews are dismissed when new commits are pushed)
- Branch must be up to date: Required before merging

### Branch Naming Convention

We follow a standardized naming pattern for all branches:

`type/service-name/FeatureName`

The three segments are:

1. **type** the kind of change (see table below).
2. **service-name** the microservice the branch targets (e.g. `player-service`, `game-service`, `exam-service`, `world-service`, `zombie-service`, `resource-service`, `base-service`, `crafting-service`).
3. **FeatureName**  a short PascalCase or kebab-case description of the change. Use present tense for actions.

### Branch Types

| Prefix      | Purpose                   | Example                                            |
| ----------- | ------------------------- | -------------------------------------------------- |
| `feat/`     | New functionality         | `feat/offer-service/CreateOfferEndpoint`           |
| `fix/`      | Bug fixes                 | `fix/zombie-service/ProfessorSpawnLoop`            |
| `hotfix/`   | Critical production fixes | `hotfix/exam-service/GradingCrashOnTimeout`        |
| `refactor/` | Code restructuring        | `refactor/resource-service/IdempotentTransactions` |
| `docs/`     | Documentation updates     | `docs/contracts/RefreshApiEndpoints`               |
| `chore/`    | Maintenance tasks         | `chore/player-service/BumpDependencies`            |
| `test/`     | Adding or fixing tests    | `test/crafting-service/AtomicRecipeCases`          |

### Naming Guidelines

- Use lowercase letters in the **type** and **service-name** segments.
- Use kebab-case or PascalCase in the **FeatureName** segment; keep it concise but descriptive.
- Always include the **service-name** so it is clear which microservice the branch belongs to.
- If the change spans multiple services, use the most relevant primary service or `shared/` (e.g. `feat/shared/CrossServiceEventSchema`).
- Always reference the related issue number in the PR description (not necessarily in the branch name).

## Merging Strategy

**Strategy:** Squash and Merge

### Benefits

- Clean, linear commit history
- Combines all commits from a feature branch into a single commit
- Easier to track features and revert if necessary

### Process

1. Create feature branch from `dev`
2. Make commits with clear, descriptive messages
3. Open Pull Request to `dev`
4. After approval, squash and merge
5. Delete feature branch after merge

### Future Automation

- GitHub Actions will be configured for automatic testing
- All PRs must pass automated tests before merging
- Code coverage reports will be generated

## Code Review Guidelines

### For Reviewers

- Verify functionality matches game-design requirements.
- Test the changes locally when possible.
- Provide constructive feedback tied to the grading rubric above.

### For Authors

- Respond to human feedback promptly and professionally.
- Make requested changes in separate commits so the diff stays reviewable.
- Re-request review after addressing feedback.
- Keep PRs focused and reasonably sized to keep the grading signal meaningful.

## Workflow Summary

1. **Create Issue:** In each lab appear, meeting with team to document the feature/bug with clear requirements
2. **Create Branch:** Use proper naming convention from `dev`
3. **Develop:** Make commits with clear, descriptive messages
4. **Test:** Verify functionality
5. **Create PR:** Follow template and provide complete information
6. **Review:** Address feedback and get required approvals
7. **Merge:** Squash and merge to `dev`
8. **Deploy:** Regular releases from `dev` to `main`

## Tools & Resources

- GitHub Desktop: For GUI-based Git operations
- VS Code: Recommended IDE with Git integration
- GitHub CLI: For command-line operations
- Conventional Commits: For consistent commit messages
- Postman: For designing, sharing and running the REST API requests defined in this contract.
- DBeaver: For browsing and querying the PostgreSQL databases of each service/



