# Crafting Service

Lets players combine resources into survival equipment, for example **Wood + Metal → Barricade Kit**. It owns the recipe catalog, the unlock rules, and every crafting job.

Built with **Python 3 + Flask**. Runs on port **5008**.

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

The first run creates a virtual environment in `.venv/` and installs `requirements.txt` into it, so nothing is installed globally. The service is then available at **http://localhost:5008**. Stop it with `Ctrl+C`.

### Option 2: manually

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

### Option 3: Docker

```bash
docker build -t crafting-service:1.0.0 .
docker run --rm -p 5008:5008 crafting-service:1.0.0
```

### Change the port

Set the `PORT` environment variable, for example `PORT=6000 python app.py`. On Windows PowerShell, run `$env:PORT=6000; python app.py`.

### Check that it works

```bash
curl http://localhost:5008/api/crafting/recipes -H "Authorization: Bearer player-token"
```

You should get the five seed recipes. `requests.http` has a ready-made request for every endpoint.

---

## Authentication

Every request needs an `Authorization: Bearer <token>` header. The service doesn't verify real JWTs yet. Instead, demo tokens map to roles:

| Token             | Role        | Used for                                          |
|-------------------|-------------|---------------------------------------------------|
| `service-token`   | `service`   | Calls from other services (e.g. Game Service)     |
| `moderator-token` | `moderator` | Creating, editing, deleting recipes               |
| anything else     | `player`    | Normal client calls                               |

A missing token returns `401`. The wrong role returns `403`.

---

## Endpoints

| Method | Path                                         | Who       | Description                              |
|--------|----------------------------------------------|-----------|------------------------------------------|
| POST   | `/api/crafting/recipes`                      | moderator | Create a recipe                          |
| GET    | `/api/crafting/recipes[?player_id=]`         | any       | List recipes (+ availability for player) |
| GET    | `/api/crafting/recipes/{recipe_id}`          | any       | Get a recipe                             |
| PATCH  | `/api/crafting/recipes/{recipe_id}`          | moderator | Rebalance a recipe                       |
| DELETE | `/api/crafting/recipes/{recipe_id}`          | moderator | Retire a recipe (not while jobs run)     |
| GET    | `/api/crafting/players/{player_id}/recipes`  | any       | Unlocked recipes + missing materials     |
| POST   | `/api/crafting/jobs`                         | any       | Craft an item                            |
| GET    | `/api/crafting/jobs/{job_id}`                | any       | Get a job                                |
| GET    | `/api/crafting/jobs?player_id=&status=`      | any       | List jobs                                |
| DELETE | `/api/crafting/jobs/{job_id}`                | any       | Cancel an in-progress job and refund it  |

Errors always look like `{"error": "message"}`. Some errors add fields, such as `missing` or `locked_by`.

### Example: craft an item

```
POST /api/crafting/jobs
Authorization: Bearer player-token
```
```json
{ "event_id": "evt-uuid-craft-001", "player_id": "player-uuid-123", "recipe_id": "barricade_kit", "quantity": 1, "base_id": "base-uuid-001" }
```
`201 Created`
```json
{
  "job_id": "job-48aa54fa",
  "player_id": "player-uuid-123",
  "recipe_id": "barricade_kit",
  "quantity": 1,
  "status": "completed",
  "consumed": [{ "item_id": "wood-01", "count": 3 }, { "item_id": "metal-01", "count": 1 }],
  "produced": { "item_id": "item-barricade_kit-1", "code": "BARRICADE_KIT", "quantity": 1 },
  "completes_at": "2026-10-06T16:26:54Z"
}
```

Possible errors are `403` (recipe locked), `402` (not enough materials), `409` (facility not built) and `404` (unknown recipe).

---

## How it works

- **Crafting steps.** A craft goes through these steps in order:
  1. Check that the recipe is unlocked.
  2. Check that the player has the materials.
  3. Remove the materials.
  4. Give the crafted item to the player.

  If step 4 fails, the materials are refunded and the job is marked `failed`. This is called a *compensating action*.
- **Timed crafting.** A craft started with a player token completes immediately. A craft started with `service-token` (the Game Service's timed action) stays `in_progress` until `completes_at`. Until then it can be cancelled for a full refund.
- **Idempotency.** A retry with the same `event_id` returns the stored job and doesn't craft again.
- **Storage.** Data lives in memory, so it resets to the seed data when the service restarts.
- **Mocked dependencies.** The Resource, Player, Exam, World and Base services are replaced by in-memory fakes in `clients.py`, with this seed data:
  - `player-uuid-123` is level 3 and has 20 wood, 10 metal, 5 food and 2 chemicals
  - `base-uuid-001` has a workbench
  - the exam `math-101` is not passed yet
- **Seed recipes.** The service starts with `barricade_kit`, `improvised_weapon`, `energy_booster`, `zombie_detector` and `exam_cheat_sheet`.

## Project structure

```
crafting-service/
├── app.py            Flask routes (start reading here)
├── clients.py        Fake Resource / Player / Exam / World / Base services
├── common.py         Auth decorator, error format, ids, timestamps
├── test_crafting.py  Tests (pytest)
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


