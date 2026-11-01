# Base Service

Manages the players' survival base, which starts as the **FAF Cab** room. It covers the base itself, the rooms annexed into it, and the barricades protecting those rooms.

Built with **Python 3 + Flask**. Runs on port **5007**.

---

## How to run

### Requirements

- **Python 3.10+** (check with `python --version` or `python3 --version`)
- **Docker** (only for the Docker option)

### Option 1: the run script (recommended)

```bash
python run.py            # install dependencies and start the service
python run.py test       # install dependencies and run the tests
python run.py docker     # build the Docker image and run it
```

On macOS/Linux, use `python3` if `python` is not found.

The first run creates a virtual environment in `.venv/` and installs `requirements.txt` into it, so nothing is installed globally. The service is then available at **http://localhost:5007**. Stop it with `Ctrl+C`.

### Option 2: manually

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

### Option 3: Docker

```bash
docker build -t base-service:1.0.0 .
docker run --rm -p 5007:5007 base-service:1.0.0
```

### Change the port

Set the `PORT` environment variable, for example `PORT=6000 python app.py`. On Windows PowerShell, run `$env:PORT=6000; python app.py`.

### Check that it works

```bash
curl -X POST http://localhost:5007/api/bases \
  -H "Authorization: Bearer service-token" \
  -H "Content-Type: application/json" \
  -d '{"event_id":"evt-1","lobby_id":"lobby-1","map_id":"map-1","home_room_id":"faf-cab"}'
```

You should get `201 Created` with the new base. `requests.http` has a ready-made request for every endpoint.

---

## Authentication

Every request needs an `Authorization: Bearer <token>` header. The service doesn't verify real JWTs yet. Instead, demo tokens map to roles:

| Token             | Role        | Used for                                          |
|-------------------|-------------|---------------------------------------------------|
| `service-token`   | `service`   | Calls from other services (e.g. Game Service)     |
| `moderator-token` | `moderator` | Admin actions                                     |
| anything else     | `player`    | Normal client calls                               |

A missing token returns `401`. The wrong role returns `403`.

---

## Endpoints

| Method | Path                                             | Who     | Description                     |
|--------|--------------------------------------------------|---------|---------------------------------|
| POST   | `/api/bases`                                     | service | Create the base of a lobby      |
| GET    | `/api/bases/{base_id}`                           | any     | Full snapshot of a base         |
| GET    | `/api/bases?lobby_id={lobby_id}`                 | service | Find a base by its lobby        |
| PATCH  | `/api/bases/{base_id}`                           | any     | Rename / change level           |
| DELETE | `/api/bases/{base_id}`                           | service | Delete a base                   |
| GET    | `/api/bases/{base_id}/rooms`                     | any     | List rooms                      |
| POST   | `/api/bases/{base_id}/rooms`                     | service | Annex a cleared room            |
| DELETE | `/api/bases/{base_id}/rooms/{room_id}`           | service | Remove a room (not the homeroom)|
| GET    | `/api/bases/{base_id}/rooms/{room_id}/barricade` | any     | Get a barricade                 |
| POST   | `/api/bases/{base_id}/rooms/{room_id}/barricade` | service | Build / reinforce               |
| PATCH  | `/api/bases/{base_id}/rooms/{room_id}/barricade` | service | Repair / damage                 |
| DELETE | `/api/bases/{base_id}/rooms/{room_id}/barricade` | service | Destroy                         |

Errors always look like `{"error": "message"}`. Some errors add fields; a `402` response, for example, also lists `missing`.

### Example: create a base

```
POST /api/bases
Authorization: Bearer service-token
```
```json
{ "event_id": "evt-uuid-base-001", "lobby_id": "lobby-uuid-789", "map_id": "map-uuid-001", "home_room_id": "faf-cab" }
```
`201 Created`
```json
{
  "base_id": "base-3f2a9c1e",
  "lobby_id": "lobby-uuid-789",
  "name": "FAF Cab",
  "level": 1,
  "rooms": [{ "room_id": "faf-cab", "reinforced": false, "barricade_level": 0 }],
  "facilities": [],
  "storage": { "capacity": 50, "used": 0 }
}
```

---

## How it works

- **Storage.** Data lives in memory (Python dictionaries), so it resets when the service restarts.
- **Idempotency.** Every mutating call carries an `event_id`. A retry with the same `event_id` returns the first response, and nothing is charged twice.
- **Atomic changes.** A lock stops two requests from interleaving. Each handler runs all its checks before it changes anything, so a failed request leaves no partial state.
- **Mocked dependencies.** The Resource, World and Player services are replaced by in-memory fakes in `clients.py`, with this seed data:
  - every new base starts with 50 wood, 20 metal and 10 food
  - rooms `faf-cab`, `lab-a1` and `lab-a2` are cleared; `hallway-3` is not
  - `player-uuid-123` owns one `BARRICADE_KIT`
- **Game rules.** These are constants at the top of `app.py`:
  - annexing a room costs 10 wood
  - a barricade costs 5 wood + 2 metal, and a repair costs 2 wood
  - barricade max HP is 100, 150 and 200 for levels 1–3

## Project structure

```
base-service/
├── app.py            Flask routes (start reading here)
├── clients.py        Fake Resource / World / Player services
├── common.py         Auth decorator, error format, ids, timestamps
├── test_base.py      Tests (pytest)
├── run.py            Build & run script
├── requests.http     Example request for every endpoint
├── requirements.txt
└── Dockerfile
```

## Lab 3: status, logs, Service Discovery

- `GET /status` – is this instance alive: `{"service", "instance_id", "status": "UP",
  "uptime_seconds", "requests_total"}`. Same shape in every service.
- Logs: one line when a request arrives and one when it leaves (status + duration), one line per
  business change and per rejected request. Every line shows the gateway's `X-Request-Id`.
- With `SERVICE_DISCOVERY_URL` set (in compose: `http://service-discovery:8761`) the instance
  registers itself on startup, sends a heartbeat every 10 s and deregisters on shutdown.
  Without it (laptop, tests) nothing is registered.
- Every response carries `X-Instance-Id`, so you can see which instance answered.


