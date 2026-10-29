"""Crafting Service: players combine resources into survival equipment.

Endpoints implemented in this lab:

    Recipe CRUD
      POST   /api/crafting/recipes                    create   (moderator)
      GET    /api/crafting/recipes[?player_id=]       list (+ availability)
      GET    /api/crafting/recipes/<recipe_id>        get one
      PATCH  /api/crafting/recipes/<recipe_id>        rebalance (moderator)
      DELETE /api/crafting/recipes/<recipe_id>        retire   (moderator)

    Player recipes
      GET    /api/crafting/players/<player_id>/recipes

    Crafting jobs
      POST   /api/crafting/jobs                       craft (idempotent)
      GET    /api/crafting/jobs/<job_id>              get one
      GET    /api/crafting/jobs?player_id=&status=    list
      DELETE /api/crafting/jobs/<job_id>              cancel + refund

The interesting endpoint is POST /api/crafting/jobs. It talks to two other
services and must never leave the player robbed of materials:

    check unlock -> check materials -> REMOVE materials -> CREDIT item
                                                  |
                         credit failed?  ->  REFUND materials, job = "failed"

Undoing a step that already happened in another service is called a
COMPENSATING ACTION; it is how you get "all or nothing" across services.
"""
import os
import threading
from datetime import datetime, timedelta, timezone

from flask import Flask, g, jsonify, request

from clients import (PlayerServiceError, base_service, exam_service,
                     player_service, resource_service, world_service)
from common import error, missing_fields, new_id, now_iso, read_json, require_auth
from observability import init_observability, start_registration

app = Flask(__name__)
app.json.sort_keys = False  # keep the field order from the spec
# Lab 3: request logs (with the gateway's request id) + GET /status
log = init_observability(app, "crafting-service")

# ---------------------------------------------------------------------------
# Storage (in memory)
# ---------------------------------------------------------------------------
recipes = {}           # recipe_id -> recipe dict
jobs = {}              # job_id    -> job dict
processed_events = {}  # event_id  -> (response_body, status_code)
lock = threading.Lock()

SEED_RECIPES = [
    {"recipe_id": "barricade_kit", "name": "Barricade Kit",
     "inputs": [{"item_id": "wood-01", "count": 3}, {"item_id": "metal-01", "count": 1}],
     "output": {"code": "BARRICADE_KIT", "type": "equipment", "quantity": 1},
     "craft_time_seconds": 60,
     "requires": {"player_level": 1, "facility": "workbench"}},
    {"recipe_id": "improvised_weapon", "name": "Improvised Weapon",
     "inputs": [{"item_id": "paper-01", "count": 2}, {"item_id": "metal-01", "count": 2}],
     "output": {"code": "IMPROVISED_WEAPON", "type": "weapon", "quantity": 1},
     "craft_time_seconds": 90, "requires": {"player_level": 2}},
    {"recipe_id": "energy_booster", "name": "Energy Booster",
     "inputs": [{"item_id": "food-01", "count": 1}, {"item_id": "chemicals-01", "count": 1}],
     "output": {"code": "ENERGY_BOOSTER", "type": "consumable", "quantity": 1},
     "craft_time_seconds": 30, "requires": {}},
    {"recipe_id": "zombie_detector", "name": "Zombie Detector",
     "inputs": [{"item_id": "metal-01", "count": 4}, {"item_id": "electronics-01", "count": 2}],
     "output": {"code": "ZOMBIE_DETECTOR", "type": "equipment", "quantity": 1},
     "craft_time_seconds": 180, "requires": {"zone_id": "zone-lab-wing", "player_level": 5}},
    {"recipe_id": "exam_cheat_sheet", "name": "Exam Cheat Sheet",
     "inputs": [{"item_id": "paper-01", "count": 1}, {"item_id": "wood-01", "count": 1}],
     "output": {"code": "EXAM_CHEAT_SHEET", "type": "consumable", "quantity": 1},
     "craft_time_seconds": 45, "requires": {"exam_id": "math-101"}},
]


def reset_state():
    """Back to the seed data. Used by the tests."""
    recipes.clear()
    jobs.clear()
    processed_events.clear()
    for recipe in SEED_RECIPES:
        recipes[recipe["recipe_id"]] = {**recipe, "requires": dict(recipe["requires"])}
    for client in (resource_service, player_service, exam_service, world_service, base_service):
        client.__init__()


reset_state()


# ---------------------------------------------------------------------------
# Idempotency helpers (same idea as in the Base Service)
# ---------------------------------------------------------------------------
def finish(event_id, body, status):
    processed_events[event_id] = (body, status)
    log.info("Event %s processed: %s %s -> %s", event_id, request.method, request.path, status)
    return jsonify(body), status


def replay(saved):
    body, status = saved
    log.info("Duplicate event: replaying the saved %s response, nothing changed (idempotency)", status)
    return jsonify(body), status


# ---------------------------------------------------------------------------
# Recipe validation
# ---------------------------------------------------------------------------
REQUIREMENT_KEYS = ("exam_id", "player_level", "zone_id", "facility")


def validate_items(items):
    """inputs must look like [{"item_id": "wood-01", "count": 3}, ...]."""
    if not isinstance(items, list) or not items:
        return "'inputs' must be a non-empty list."
    for item in items:
        if not isinstance(item, dict) or not item.get("item_id") \
                or not isinstance(item.get("count"), int) or item["count"] < 1:
            return "Every input needs an 'item_id' and a positive integer 'count'."
    return None


def validate_recipe(body):
    """Error message for the fields present in `body`, or None if all are OK.
    Shared by create (all fields present) and update (some fields present)."""
    if "name" in body and (not isinstance(body["name"], str) or not body["name"].strip()):
        return "'name' must be a non-empty string."
    if "inputs" in body:
        problem = validate_items(body["inputs"])
        if problem:
            return problem
    if "output" in body:
        out = body["output"]
        if not isinstance(out, dict) or not out.get("code") \
                or not isinstance(out.get("quantity", 1), int):
            return "'output' needs a 'code' (and an integer 'quantity')."
    if "craft_time_seconds" in body:
        t = body["craft_time_seconds"]
        if not isinstance(t, int) or t < 0:
            return "'craft_time_seconds' must be a non-negative integer."
    if "requires" in body and body["requires"] is not None \
            and not isinstance(body["requires"], dict):
        return "'requires' must be an object."
    return None


def clean_requires(requires):
    """The spec says an absent field means "no gate", so drop the nulls:
    {"exam_id": None, "player_level": 1} -> {"player_level": 1}"""
    requires = requires or {}
    return {k: v for k, v in requires.items() if k in REQUIREMENT_KEYS and v is not None}


# ---------------------------------------------------------------------------
# Unlock rules: why can't this player craft this recipe?
# ---------------------------------------------------------------------------
def locked_by(recipe, player_id):
    """List of reasons, e.g. ["exam:math-101", "level:5"]. Empty = unlocked.
    (The facility is not a player lock; it is checked per base when crafting.)"""
    req = recipe["requires"]
    reasons = []
    if "player_level" in req and player_service.get_level(player_id) < req["player_level"]:
        reasons.append(f"level:{req['player_level']}")
    if "exam_id" in req and not exam_service.has_passed(player_id, req["exam_id"]):
        reasons.append(f"exam:{req['exam_id']}")
    if "zone_id" in req and not world_service.is_zone_unlocked(req["zone_id"]):
        reasons.append(f"zone:{req['zone_id']}")
    return reasons


def materials_for(recipe, quantity):
    """Recipe inputs multiplied by how many items we craft."""
    return [{"item_id": i["item_id"], "count": i["count"] * quantity} for i in recipe["inputs"]]


# ===========================================================================
# RECIPE CRUD
# ===========================================================================
@app.post("/api/crafting/recipes")
@require_auth("moderator")
def create_recipe():
    body = read_json()
    missing = missing_fields(body, "recipe_id", "name", "inputs", "output", "craft_time_seconds")
    if missing:
        return error(f"Missing fields: {', '.join(missing)}", 400)
    problem = validate_recipe(body)
    if problem:
        return error(problem, 400)

    with lock:
        if body["recipe_id"] in recipes:
            return error("Recipe id already exists.", 409)
        recipe = {
            "recipe_id": body["recipe_id"],
            "name": body["name"].strip(),
            "inputs": body["inputs"],
            "output": {"quantity": 1, **body["output"]},
            "craft_time_seconds": body["craft_time_seconds"],
            "requires": clean_requires(body.get("requires")),
        }
        recipes[recipe["recipe_id"]] = recipe
        log.info("Recipe created: %s name=%r craft_time=%ss", recipe["recipe_id"], recipe["name"], recipe["craft_time_seconds"])
        return jsonify(recipe), 201


@app.get("/api/crafting/recipes")
@require_auth()
def list_recipes():
    player_id = request.args.get("player_id")
    if not player_id:
        return jsonify(list(recipes.values())), 200

    # With ?player_id= : a short view annotated with availability.
    result = []
    for recipe in recipes.values():
        reasons = locked_by(recipe, player_id)
        result.append({"recipe_id": recipe["recipe_id"], "name": recipe["name"],
                       "available": not reasons, "locked_by": reasons})
    return jsonify(result), 200


@app.get("/api/crafting/recipes/<recipe_id>")
@require_auth()
def get_recipe(recipe_id):
    recipe = recipes.get(recipe_id)
    if recipe is None:
        return error("Recipe not found.", 404)
    return jsonify(recipe), 200


@app.patch("/api/crafting/recipes/<recipe_id>")
@require_auth("moderator")
def update_recipe(recipe_id):
    body = read_json()
    if "recipe_id" in body and body["recipe_id"] != recipe_id:
        return error("The recipe id cannot be changed.", 400)
    problem = validate_recipe(body)
    if problem:
        return error(problem, 400)

    with lock:
        recipe = recipes.get(recipe_id)
        if recipe is None:
            return error("Recipe not found.", 404)
        for field in ("name", "inputs", "output", "craft_time_seconds"):
            if field in body:
                recipe[field] = body[field]
        if "requires" in body:
            recipe["requires"] = clean_requires(body["requires"])
        log.info("Recipe updated: %s fields=%s", recipe_id, sorted(body))
        return jsonify(recipe), 200


@app.delete("/api/crafting/recipes/<recipe_id>")
@require_auth("moderator")
def delete_recipe(recipe_id):
    with lock:
        if recipe_id not in recipes:
            return error("Recipe not found.", 404)
        busy = any(j["recipe_id"] == recipe_id and j["status"] == "in_progress"
                   for j in jobs.values())
        if busy:
            return error("Recipe has jobs in progress.", 409)
        del recipes[recipe_id]
        log.info("Recipe deleted: %s", recipe_id)
        return jsonify({"recipe_id": recipe_id, "deleted": True}), 200


# ===========================================================================
# PLAYER RECIPES
# ===========================================================================
@app.get("/api/crafting/players/<player_id>/recipes")
@require_auth()
def list_player_recipes(player_id):
    """Unlocked recipes only, each with the materials the player still lacks."""
    result = []
    for recipe in recipes.values():
        if locked_by(recipe, player_id):
            continue
        lacking = resource_service.missing_items(player_id, recipe["inputs"])
        result.append({"recipe_id": recipe["recipe_id"], "name": recipe["name"],
                       "can_craft_now": not lacking, "missing": lacking})
    return jsonify(result), 200


# ===========================================================================
# CRAFTING JOBS
# ===========================================================================
def job_view(job):
    """The public shape of a job (the spec's "Craft an Item" response)."""
    keys = ("job_id", "player_id", "recipe_id", "quantity", "status",
            "consumed", "produced", "completes_at")
    view = {k: job[k] for k in keys}
    if "refunded" in job:
        view["refunded"] = job["refunded"]
    return view


def credit_output(job):
    """Give the crafted item to the player. If the Player Service fails,
    refund the materials (compensating action) and mark the job failed."""
    output = recipes[job["recipe_id"]]["output"]
    quantity = output["quantity"] * job["quantity"]
    try:
        item_id = player_service.credit_item(job["player_id"], output["code"], quantity)
    except PlayerServiceError as exc:
        log.error("Player Service could not credit %s x%s to %s (%s): refunding the materials, job %s failed",
                  output["code"], quantity, job["player_id"], exc, job["job_id"])
        resource_service.add(job["player_id"], job["consumed"])
        job["status"] = "failed"
        job["refunded"] = job["consumed"]
        return
    job["status"] = "completed"
    job["produced"] = {"item_id": item_id, "code": output["code"], "quantity": quantity}
    log.info("Job %s completed: %s x%s credited to player %s", job["job_id"], output["code"], quantity, job["player_id"])


def complete_if_due(job):
    """Timed jobs are finished "lazily": the first time someone looks at the
    job after `completes_at`, we credit the item. (A real service would use a
    background worker or a message from the Game Service.)

    Note: timestamps in the "2026-09-10T14:01:00Z" format sort correctly as
    plain strings, so a simple string comparison is enough here."""
    if job["status"] == "in_progress" and now_iso() >= job["completes_at"]:
        credit_output(job)


@app.post("/api/crafting/jobs")
@require_auth()
def create_job():
    body = read_json()
    missing = missing_fields(body, "event_id", "player_id", "recipe_id")
    if missing:
        return error(f"Missing fields: {', '.join(missing)}", 400)
    quantity = body.get("quantity", 1)
    if not isinstance(quantity, int) or quantity < 1:
        return error("'quantity' must be a positive integer.", 400)

    with lock:
        # A retry of an event we already handled: same answer, no second craft.
        saved = processed_events.get(body["event_id"])
        if saved:
            return replay(saved)

        player_id = body["player_id"]
        recipe = recipes.get(body["recipe_id"])
        if recipe is None:
            return error("Recipe not found.", 404)

        # --- 1. Checks (nothing has changed yet) -------------------------
        reasons = locked_by(recipe, player_id)
        if reasons:
            return error("Recipe locked.", 403, locked_by=reasons)

        facility = recipe["requires"].get("facility")
        if facility and not base_service.has_facility(body.get("base_id"), facility):
            return error(f"Facility '{facility}' not built in base.", 409)

        needed = materials_for(recipe, quantity)
        lacking = resource_service.missing_items(player_id, needed)
        if lacking:
            return error("Insufficient materials.", 402, missing=lacking)

        # --- 2. Consume the materials ------------------------------------
        resource_service.remove(player_id, needed)
        log.info("Materials consumed for %s x%s by player %s: %s", recipe["recipe_id"], quantity, player_id, needed)

        craft_time = timedelta(seconds=recipe["craft_time_seconds"] * quantity)
        completes_at = datetime.now(timezone.utc) + craft_time
        job = {
            "job_id": new_id("job"),
            "player_id": player_id,
            "recipe_id": recipe["recipe_id"],
            "quantity": quantity,
            "status": "in_progress",
            "consumed": needed,
            "produced": None,
            "completes_at": completes_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "created_at": now_iso(),
        }

        # --- 3. Credit the item ------------------------------------------
        # A player crafting from the crafting screen gets the item right away.
        # The Game Service (service token) uses crafting as a *timed action*:
        # the job stays in_progress and is credited after completes_at.
        if g.role != "service":
            credit_output(job)

        jobs[job["job_id"]] = job
        log.info("Job created: %s recipe=%s player=%s status=%s completes_at=%s",
                 job["job_id"], recipe["recipe_id"], player_id, job["status"], job["completes_at"])

        if job["status"] == "failed":
            body_out = {"error": "Could not deliver the item; materials were refunded.",
                        "job": job_view(job)}
            return finish(body["event_id"], body_out, 502)
        return finish(body["event_id"], job_view(job), 201)


@app.get("/api/crafting/jobs/<job_id>")
@require_auth()
def get_job(job_id):
    with lock:
        job = jobs.get(job_id)
        if job is None:
            return error("Job not found.", 404)
        complete_if_due(job)
        return jsonify(job_view(job)), 200


@app.get("/api/crafting/jobs")
@require_auth()
def list_jobs():
    player_id = request.args.get("player_id")
    status = request.args.get("status")
    with lock:
        result = []
        for job in jobs.values():
            complete_if_due(job)
            if player_id and job["player_id"] != player_id:
                continue
            if status and job["status"] != status:
                continue
            result.append({"job_id": job["job_id"], "recipe_id": job["recipe_id"],
                           "status": job["status"], "created_at": job["created_at"]})
        return jsonify({"jobs": result}), 200


@app.delete("/api/crafting/jobs/<job_id>")
@require_auth()
def cancel_job(job_id):
    with lock:
        job = jobs.get(job_id)
        if job is None:
            return error("Job not found.", 404)
        complete_if_due(job)
        if job["status"] != "in_progress":
            return error(f"Job already {job['status']}.", 409)

        resource_service.add(job["player_id"], job["consumed"])  # give materials back
        job["status"] = "cancelled"
        job["refunded"] = job["consumed"]
        log.info("Job cancelled: %s, refunded %s to player %s", job_id, job["consumed"], job["player_id"])
        return jsonify({"job_id": job_id, "status": "cancelled",
                        "refunded": job["refunded"]}), 200


@app.get("/health")
def health():
    """Liveness check used by the gateway's GET /health (no token needed)."""
    return jsonify({"status": "UP"}), 200


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5008))
    start_registration("crafting-service", port)  # Lab 3: register with the Service Discovery (if configured)
    app.run(host="0.0.0.0", port=port, debug=os.environ.get("FLASK_DEBUG", "1") == "1")


    