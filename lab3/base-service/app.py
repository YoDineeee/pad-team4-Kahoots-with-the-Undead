"""Base Service: the players' survival base (it starts as the FAF Cab room).

Endpoints implemented in this lab:

    Base CRUD
      POST   /api/bases                                    create (idempotent)
      GET    /api/bases/<base_id>                          full snapshot
      GET    /api/bases?lobby_id=<lobby_id>                find base by lobby
      PATCH  /api/bases/<base_id>                          rename / set level
      DELETE /api/bases/<base_id>                          delete

    Rooms
      GET    /api/bases/<base_id>/rooms                    list
      POST   /api/bases/<base_id>/rooms                    annex (idempotent)
      DELETE /api/bases/<base_id>/rooms/<room_id>          remove

    Barricades
      GET    /api/bases/<base_id>/rooms/<room_id>/barricade
      POST   /api/bases/<base_id>/rooms/<room_id>/barricade   build / reinforce
      PATCH  /api/bases/<base_id>/rooms/<room_id>/barricade   repair / damage
      DELETE /api/bases/<base_id>/rooms/<room_id>/barricade   destroy

Two ideas to look out for while reading the code:

1. IDEMPOTENCY: mutating calls carry an `event_id`. The first time we see
   it we do the work and save the response. If the same event_id comes again
   (e.g. the Game Service retried after a timeout), we return the saved
   response and do NOT charge resources a second time.

2. "TRANSACTIONS": the spec runs every change inside a database transaction
   together with the Resource Service call. With in-memory storage we get the
   same result by (a) holding a lock so two requests can't interleave and
   (b) running every check BEFORE changing anything, so a failure never
   leaves a half-done change.
"""
import os
import threading

from flask import Flask, jsonify, request

from clients import player_service, resource_service, world_service
from common import error, missing_fields, new_id, now_iso, read_json, require_auth
from observability import init_observability, start_registration

app = Flask(__name__)
app.json.sort_keys = False  # keep the field order from the spec
# Lab 3: request logs (with the gateway's request id) + GET /status
log = init_observability(app, "base-service")

# ---------------------------------------------------------------------------
# Game rules (in the full service they would come from the upgrade catalog)
# ---------------------------------------------------------------------------
HOME_STORAGE_CAPACITY = 50
ANNEX_COST = [{"item_id": "wood-01", "count": 10}]
BARRICADE_COST = [{"item_id": "wood-01", "count": 5}, {"item_id": "metal-01", "count": 2}]
REPAIR_COST = [{"item_id": "wood-01", "count": 2}]
MAX_BARRICADE_LEVEL = 3


def max_hp_for(level):
    """Barricade HP by level: 0 -> 0, 1 -> 100, 2 -> 150, 3 -> 200."""
    return 0 if level == 0 else 50 + 50 * level


# ---------------------------------------------------------------------------
# Storage: plain dictionaries in memory (data is lost when the process stops)
# ---------------------------------------------------------------------------
bases = {}             # base_id  -> base dict (see create_base for its shape)
processed_events = {}  # event_id -> (response_body, status_code)
lock = threading.Lock()


def reset_state():
    """Wipe everything. Used by the tests to start from a clean slate."""
    bases.clear()
    processed_events.clear()
    resource_service.__init__()
    world_service.__init__()
    player_service.__init__()


# ---------------------------------------------------------------------------
# Idempotency helpers
# ---------------------------------------------------------------------------
def already_processed(event_id):
    """Saved (body, status) if this event_id was handled before, else None."""
    return processed_events.get(event_id)


def finish(event_id, body, status):
    """Remember the response for this event_id, then send it."""
    processed_events[event_id] = (body, status)
    log.info("Event %s processed: %s %s -> %s", event_id, request.method, request.path, status)
    return jsonify(body), status


def replay(saved):
    body, status = saved
    log.info("Duplicate event: replaying the saved %s response, nothing changed (idempotency)", status)
    return jsonify(body), status


# ---------------------------------------------------------------------------
# Lookup and "to JSON" helpers
# ---------------------------------------------------------------------------
def find_base_by_lobby(lobby_id):
    for base in bases.values():
        if base["lobby_id"] == lobby_id:
            return base
    return None


def room_short(room):
    """The room as it appears inside a base snapshot."""
    return {
        "room_id": room["room_id"],
        "reinforced": room["reinforced"],
        "barricade_level": room["barricade_level"],
    }


def base_snapshot(base):
    """The full "Get a Base" response."""
    return {
        "base_id": base["base_id"],
        "lobby_id": base["lobby_id"],
        "name": base["name"],
        "level": base["level"],
        "rooms": [room_short(r) for r in base["rooms"].values()],
        "facilities": base["facilities"],
        "storage": base["storage"],
        "decorations": base["decorations"],
    }


def barricade_view(room):
    return {
        "room_id": room["room_id"],
        "barricade_level": room["barricade_level"],
        "hp": room["hp"],
        "max_hp": max_hp_for(room["barricade_level"]),
    }


def new_room(room_id):
    return {
        "room_id": room_id,
        "reinforced": False,
        "barricade_level": 0,
        "hp": 0,
        "annexed_at": now_iso(),
    }


# ===========================================================================
# BASE CRUD
# ===========================================================================
@app.post("/api/bases")
@require_auth("service")
def create_base():
    body = read_json()
    missing = missing_fields(body, "event_id", "lobby_id", "map_id", "home_room_id")
    if missing:
        return error(f"Missing fields: {', '.join(missing)}", 400)

    with lock:
        # Same event again -> same answer, no new base.
        saved = already_processed(body["event_id"])
        if saved:
            return replay(saved)

        # A *different* event for a lobby that already has a base -> conflict.
        if find_base_by_lobby(body["lobby_id"]):
            return error("Lobby already has a base.", 409)

        home = body["home_room_id"]
        base = {
            "base_id": new_id("base"),
            "lobby_id": body["lobby_id"],
            "map_id": body["map_id"],
            "home_room_id": home,
            "name": "FAF Cab",
            "level": 1,
            "rooms": {home: new_room(home)},  # the homeroom is annexed automatically
            "facilities": [],
            "storage": {"capacity": HOME_STORAGE_CAPACITY, "used": 0},
            "decorations": [],
        }
        bases[base["base_id"]] = base
        log.info("Base created: %s lobby=%s map=%s home_room=%s", base["base_id"], body["lobby_id"], body["map_id"], home)

        response = base_snapshot(base)
        del response["decorations"]  # the spec's 201 response has no decorations
        return finish(body["event_id"], response, 201)


@app.get("/api/bases/<base_id>")
@require_auth()
def get_base(base_id):
    base = bases.get(base_id)
    if base is None:
        return error("Base not found.", 404)
    return jsonify(base_snapshot(base)), 200


@app.get("/api/bases")
@require_auth("service")
def get_base_by_lobby():
    lobby_id = request.args.get("lobby_id")
    if not lobby_id:
        return error("Query parameter 'lobby_id' is required.", 400)
    base = find_base_by_lobby(lobby_id)
    if base is None:
        return error("Base not found.", 404)
    return jsonify(base_snapshot(base)), 200


@app.patch("/api/bases/<base_id>")
@require_auth()
def update_base(base_id):
    body = read_json()
    with lock:
        base = bases.get(base_id)
        if base is None:
            return error("Base not found.", 404)

        # Only name and level can change here. Rooms, barricades, etc.
        # have their own endpoints.
        if "name" in body:
            if not isinstance(body["name"], str) or not body["name"].strip():
                return error("'name' must be a non-empty string.", 400)
            base["name"] = body["name"].strip()
        if "level" in body:
            if not isinstance(body["level"], int) or body["level"] < 1:
                return error("'level' must be a positive integer.", 400)
            base["level"] = body["level"]

        log.info("Base updated: %s name=%r level=%s", base_id, base["name"], base["level"])
        return jsonify({"base_id": base_id, "name": base["name"], "level": base["level"]}), 200


@app.delete("/api/bases/<base_id>")
@require_auth("service")
def delete_base(base_id):
    with lock:
        if bases.pop(base_id, None) is None:
            return error("Base not found.", 404)
        log.info("Base deleted: %s", base_id)
        return jsonify({"base_id": base_id, "deleted": True}), 200


# ===========================================================================
# ROOMS
# ===========================================================================
@app.get("/api/bases/<base_id>/rooms")
@require_auth()
def list_rooms(base_id):
    base = bases.get(base_id)
    if base is None:
        return error("Base not found.", 404)
    rooms = [{**room_short(r), "annexed_at": r["annexed_at"]} for r in base["rooms"].values()]
    return jsonify(rooms), 200


@app.post("/api/bases/<base_id>/rooms")
@require_auth("service")
def annex_room(base_id):
    body = read_json()
    missing = missing_fields(body, "event_id", "room_id", "player_id")
    if missing:
        return error(f"Missing fields: {', '.join(missing)}", 400)

    with lock:
        saved = already_processed(body["event_id"])
        if saved:
            return replay(saved)

        base = bases.get(base_id)
        if base is None:
            return error("Base not found.", 404)

        # --- 1. Checks (nothing has changed yet) -------------------------
        room_id = body["room_id"]
        room = world_service.get_room(room_id)            # World Service
        if room is None:
            return error("Room not found.", 404)
        if not room["cleared"] or room_id in base["rooms"]:
            return error("Room is not cleared or is already part of the base.", 409)

        lacking = resource_service.missing_items(base_id, ANNEX_COST)  # Resource Service
        if lacking:
            return error("Insufficient resources.", 402, missing=lacking)

        # --- 2. Every check passed: pay, then change our own data --------
        resource_service.remove(base_id, ANNEX_COST)
        base["rooms"][room_id] = new_room(room_id)
        log.info("Room annexed: base=%s room=%s by player=%s cost=%s", base_id, room_id, body["player_id"], ANNEX_COST)

        response = {**room_short(base["rooms"][room_id]), "cost_paid": ANNEX_COST}
        return finish(body["event_id"], response, 201)


@app.delete("/api/bases/<base_id>/rooms/<room_id>")
@require_auth("service")
def remove_room(base_id, room_id):
    with lock:
        base = bases.get(base_id)
        if base is None:
            return error("Base not found.", 404)
        if room_id not in base["rooms"]:
            return error("Room is not part of the base.", 404)
        if room_id == base["home_room_id"]:
            return error("The homeroom cannot be removed.", 409)

        del base["rooms"][room_id]
        log.info("Room removed from base: base=%s room=%s", base_id, room_id)
        return jsonify({"room_id": room_id, "removed": True}), 200


# ===========================================================================
# BARRICADES
# ===========================================================================
def find_room(base_id, room_id):
    """Returns (room, None) or (None, error_response)."""
    base = bases.get(base_id)
    if base is None:
        return None, error("Base not found.", 404)
    room = base["rooms"].get(room_id)
    if room is None:
        return None, error("Room is not part of the base.", 404)
    return room, None


@app.get("/api/bases/<base_id>/rooms/<room_id>/barricade")
@require_auth()
def get_barricade(base_id, room_id):
    room, err = find_room(base_id, room_id)
    if err:
        return err
    return jsonify(barricade_view(room)), 200


@app.post("/api/bases/<base_id>/rooms/<room_id>/barricade")
@require_auth("service")
def build_barricade(base_id, room_id):
    body = read_json()
    missing = missing_fields(body, "event_id", "player_id")
    if missing:
        return error(f"Missing fields: {', '.join(missing)}", 400)

    with lock:
        saved = already_processed(body["event_id"])
        if saved:
            return replay(saved)

        room, err = find_room(base_id, room_id)
        if err:
            return err
        if room["barricade_level"] >= MAX_BARRICADE_LEVEL:
            return error(f"Barricade is already at max level {MAX_BARRICADE_LEVEL}.", 409)

        # --- Pay: a barricade kit from the inventory, OR wood + metal ------
        kit = body.get("kit_item_id")
        if kit:
            if not player_service.take_item(body["player_id"], kit):   # Player Service
                return error("Player does not own that kit.", 402,
                             missing=[{"item_id": kit, "count": 1}])
            cost_paid = [{"item_id": kit, "count": 1}]
        else:
            lacking = resource_service.missing_items(base_id, BARRICADE_COST)
            if lacking:
                return error("Insufficient resources.", 402, missing=lacking)
            resource_service.remove(base_id, BARRICADE_COST)            # Resource Service
            cost_paid = BARRICADE_COST

        # --- Raise the level; a new level comes with full HP --------------
        room["barricade_level"] += 1
        room["hp"] = max_hp_for(room["barricade_level"])
        world_service.report_barricade(room_id, room["barricade_level"])  # World Service
        log.info("Barricade built: base=%s room=%s level=%s hp=%s paid=%s",
                 base_id, room_id, room["barricade_level"], room["hp"], cost_paid)

        response = {**barricade_view(room), "cost_paid": cost_paid}
        return finish(body["event_id"], response, 200)


@app.patch("/api/bases/<base_id>/rooms/<room_id>/barricade")
@require_auth("service")
def repair_or_damage_barricade(base_id, room_id):
    body = read_json()
    missing = missing_fields(body, "event_id", "operation", "amount")
    if missing:
        return error(f"Missing fields: {', '.join(missing)}", 400)
    operation, amount = body["operation"], body["amount"]
    if operation not in ("repair", "damage"):
        return error("'operation' must be 'repair' or 'damage'.", 400)
    if not isinstance(amount, int) or amount <= 0:
        return error("'amount' must be a positive integer.", 400)

    with lock:
        saved = already_processed(body["event_id"])
        if saved:
            return replay(saved)

        room, err = find_room(base_id, room_id)
        if err:
            return err
        if room["barricade_level"] == 0:
            return error("There is no barricade in this room.", 409)

        if operation == "repair":
            lacking = resource_service.missing_items(base_id, REPAIR_COST)
            if lacking:
                return error("Insufficient resources.", 402, missing=lacking)
            resource_service.remove(base_id, REPAIR_COST)
            room["hp"] = min(room["hp"] + amount, max_hp_for(room["barricade_level"]))
            destroyed = False
        else:  # damage (zombie attack, free)
            room["hp"] = max(room["hp"] - amount, 0)
            destroyed = room["hp"] == 0
            if destroyed:
                room["barricade_level"] = 0
                world_service.report_barricade(room_id, 0)

        log.info("Barricade %s: base=%s room=%s amount=%s hp=%s destroyed=%s",
                 "repaired" if operation == "repair" else "damaged", base_id, room_id, amount, room["hp"], destroyed)
        response = {**barricade_view(room), "destroyed": destroyed}
        return finish(body["event_id"], response, 200)


@app.delete("/api/bases/<base_id>/rooms/<room_id>/barricade")
@require_auth("service")
def destroy_barricade(base_id, room_id):
    with lock:
        room, err = find_room(base_id, room_id)
        if err:
            return err
        room["barricade_level"] = 0
        room["hp"] = 0
        world_service.report_barricade(room_id, 0)
        log.info("Barricade destroyed: base=%s room=%s", base_id, room_id)
        return jsonify({"room_id": room_id, "barricade_level": 0, "destroyed": True}), 200


@app.get("/health")
def health():
    """Liveness check used by the gateway's GET /health (no token needed)."""
    return jsonify({"status": "UP"}), 200


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5007))
    start_registration("base-service", port)  # Lab 3: register with the Service Discovery (if configured)
    app.run(host="0.0.0.0", port=port, debug=os.environ.get("FLASK_DEBUG", "1") == "1")
    