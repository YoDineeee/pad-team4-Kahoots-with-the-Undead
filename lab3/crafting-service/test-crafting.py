"""Run with:  cd crafting_service && pytest -v"""
import pytest

import app as crafting_app
from clients import exam_service, player_service, resource_service

PLAYER = {"Authorization": "Bearer player-token"}
SERVICE = {"Authorization": "Bearer service-token"}
MODERATOR = {"Authorization": "Bearer moderator-token"}

NEW_RECIPE = {
    "recipe_id": "torch", "name": "Torch",
    "inputs": [{"item_id": "wood-01", "count": 1}],
    "output": {"code": "TORCH", "type": "equipment", "quantity": 1},
    "craft_time_seconds": 10,
    "requires": {"exam_id": None, "player_level": 1, "zone_id": None, "facility": None},
}


@pytest.fixture
def client():
    crafting_app.reset_state()
    return crafting_app.app.test_client()


def craft(client, event_id="evt-craft-001", recipe_id="barricade_kit", headers=PLAYER, **extra):
    payload = {"event_id": event_id, "player_id": "player-uuid-123",
               "recipe_id": recipe_id, "quantity": 1, "base_id": "base-uuid-001", **extra}
    return client.post("/api/crafting/jobs", headers=headers, json=payload)


# --- Recipe CRUD ------------------------------------------------------------
def test_recipe_crud(client):
    res = client.post("/api/crafting/recipes", headers=MODERATOR, json=NEW_RECIPE)
    assert res.status_code == 201
    assert res.get_json()["requires"] == {"player_level": 1}  # nulls dropped

    dup = client.post("/api/crafting/recipes", headers=MODERATOR, json=NEW_RECIPE)
    assert dup.status_code == 409 and dup.get_json() == {"error": "Recipe id already exists."}

    res = client.patch("/api/crafting/recipes/torch", headers=MODERATOR, json={"craft_time_seconds": 5})
    assert res.get_json()["craft_time_seconds"] == 5

    assert client.get("/api/crafting/recipes/torch", headers=PLAYER).status_code == 200
    res = client.delete("/api/crafting/recipes/torch", headers=MODERATOR)
    assert res.get_json() == {"recipe_id": "torch", "deleted": True}
    res = client.get("/api/crafting/recipes/torch", headers=PLAYER)
    assert res.status_code == 404 and res.get_json() == {"error": "Recipe not found."}


def test_only_moderators_edit_recipes(client):
    assert client.post("/api/crafting/recipes", headers=PLAYER, json=NEW_RECIPE).status_code == 403


def test_bad_recipe_is_rejected(client):
    bad = {**NEW_RECIPE, "inputs": [{"item_id": "wood-01", "count": 0}]}
    assert client.post("/api/crafting/recipes", headers=MODERATOR, json=bad).status_code == 400


def test_list_recipes_with_availability(client):
    res = client.get("/api/crafting/recipes?player_id=player-uuid-123", headers=PLAYER)
    by_id = {r["recipe_id"]: r for r in res.get_json()}
    assert by_id["barricade_kit"] == {"recipe_id": "barricade_kit", "name": "Barricade Kit",
                                      "available": True, "locked_by": []}
    assert by_id["exam_cheat_sheet"]["locked_by"] == ["exam:math-101"]


def test_player_recipes_show_missing_materials(client):
    res = client.get("/api/crafting/players/player-uuid-123/recipes", headers=PLAYER)
    by_id = {r["recipe_id"]: r for r in res.get_json()}
    assert by_id["barricade_kit"]["can_craft_now"] is True
    assert by_id["improvised_weapon"]["missing"] == [{"item_id": "paper-01", "count": 2}]
    assert "exam_cheat_sheet" not in by_id  # locked recipes are hidden


# --- Jobs -------------------------------------------------------------------
def test_craft_item(client):
    res = craft(client)
    assert res.status_code == 201
    job = res.get_json()
    assert job["status"] == "completed"
    assert job["consumed"] == [{"item_id": "wood-01", "count": 3}, {"item_id": "metal-01", "count": 1}]
    assert job["produced"]["code"] == "BARRICADE_KIT"
    assert player_service.inventory["player-uuid-123"]["BARRICADE_KIT"] == 1


def test_craft_is_idempotent(client):
    first = craft(client).get_json()
    again = craft(client).get_json()
    assert first == again
    assert resource_service.get_stock("player-uuid-123")["wood-01"] == 17  # paid once
    assert player_service.inventory["player-uuid-123"]["BARRICADE_KIT"] == 1


def test_craft_errors(client):
    res = craft(client, "e1", "exam_cheat_sheet")
    assert res.status_code == 403
    assert res.get_json() == {"error": "Recipe locked.", "locked_by": ["exam:math-101"]}

    res = craft(client, "e2", base_id="base-without-workbench")
    assert res.status_code == 409
    assert res.get_json() == {"error": "Facility 'workbench' not built in base."}

    resource_service.get_stock("player-uuid-123")["metal-01"] = 0
    res = craft(client, "e3")
    assert res.status_code == 402
    assert res.get_json() == {"error": "Insufficient materials.",
                              "missing": [{"item_id": "metal-01", "count": 1}]}


def test_unlock_by_passing_exam(client):
    exam_service.passed["player-uuid-123"].add("math-101")
    resource_service.get_stock("player-uuid-123")["paper-01"] = 1
    assert craft(client, "e1", "exam_cheat_sheet").status_code == 201


def test_failed_credit_refunds_materials(client):
    player_service.fail_next_credit = True
    res = craft(client)
    assert res.status_code == 502
    assert res.get_json()["job"]["status"] == "failed"
    assert resource_service.get_stock("player-uuid-123")["wood-01"] == 20  # nothing lost


def test_timed_job_can_be_cancelled(client):
    res = craft(client, headers=SERVICE)  # Game Service -> timed action
    job = res.get_json()
    assert job["status"] == "in_progress" and job["produced"] is None

    listed = client.get("/api/crafting/jobs?player_id=player-uuid-123&status=in_progress",
                        headers=PLAYER).get_json()
    assert [j["job_id"] for j in listed["jobs"]] == [job["job_id"]]

    # Recipe can't be retired while the job runs
    assert client.delete("/api/crafting/recipes/barricade_kit", headers=MODERATOR).status_code == 409

    res = client.delete(f"/api/crafting/jobs/{job['job_id']}", headers=PLAYER)
    assert res.get_json() == {"job_id": job["job_id"], "status": "cancelled",
                              "refunded": job["consumed"]}
    assert resource_service.get_stock("player-uuid-123")["wood-01"] == 20


def test_completed_job_cannot_be_cancelled(client):
    job_id = craft(client).get_json()["job_id"]
    res = client.delete(f"/api/crafting/jobs/{job_id}", headers=PLAYER)
    assert res.status_code == 409 and res.get_json() == {"error": "Job already completed."}


def test_timed_job_completes_after_its_time(client):
    job_id = craft(client, headers=SERVICE).get_json()["job_id"]
    crafting_app.jobs[job_id]["completes_at"] = "2000-01-01T00:00:00Z"  # pretend time passed
    res = client.get(f"/api/crafting/jobs/{job_id}", headers=PLAYER)
    assert res.get_json()["status"] == "completed"
    assert res.get_json()["produced"]["code"] == "BARRICADE_KIT"


def test_health_needs_no_token(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.get_json() == {"status": "UP"}
    