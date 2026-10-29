"""Run with:  cd base_service && pytest -v"""
import pytest

import app as base_app
from clients import resource_service, world_service

SERVICE = {"Authorization": "Bearer service-token"}
PLAYER = {"Authorization": "Bearer player-token"}


@pytest.fixture
def client():
    base_app.reset_state()
    return base_app.app.test_client()


def create_base(client, event_id="evt-base-001", lobby_id="lobby-uuid-789"):
    return client.post("/api/bases", headers=SERVICE, json={
        "event_id": event_id, "lobby_id": lobby_id,
        "map_id": "map-uuid-001", "home_room_id": "faf-cab"})


# --- Base CRUD --------------------------------------------------------------
def test_create_base_returns_homeroom(client):
    res = create_base(client)
    assert res.status_code == 201
    body = res.get_json()
    assert body["name"] == "FAF Cab" and body["level"] == 1
    assert body["rooms"] == [{"room_id": "faf-cab", "reinforced": False, "barricade_level": 0}]
    assert body["storage"] == {"capacity": 50, "used": 0}


def test_create_base_is_idempotent_and_conflicts_on_new_event(client):
    first = create_base(client).get_json()
    retry = create_base(client)                       # same event_id
    assert retry.status_code == 201 and retry.get_json() == first
    other = create_base(client, event_id="evt-base-002")  # same lobby, new event
    assert other.status_code == 409
    assert other.get_json() == {"error": "Lobby already has a base."}


def test_auth_is_required(client):
    assert client.post("/api/bases", json={}).status_code == 401
    assert client.post("/api/bases", headers=PLAYER, json={}).status_code == 403


def test_get_update_delete_base(client):
    base_id = create_base(client).get_json()["base_id"]

    assert client.get(f"/api/bases/{base_id}", headers=PLAYER).status_code == 200
    by_lobby = client.get("/api/bases?lobby_id=lobby-uuid-789", headers=SERVICE)
    assert by_lobby.get_json()["base_id"] == base_id

    res = client.patch(f"/api/bases/{base_id}", headers=PLAYER, json={"name": "Fortress Cab"})
    assert res.get_json() == {"base_id": base_id, "name": "Fortress Cab", "level": 1}

    res = client.delete(f"/api/bases/{base_id}", headers=SERVICE)
    assert res.get_json() == {"base_id": base_id, "deleted": True}
    res = client.get(f"/api/bases/{base_id}", headers=PLAYER)
    assert res.status_code == 404 and res.get_json() == {"error": "Base not found."}


# --- Rooms ------------------------------------------------------------------
def annex(client, base_id, room_id="lab-a1", event_id="evt-annex-001"):
    return client.post(f"/api/bases/{base_id}/rooms", headers=SERVICE, json={
        "event_id": event_id, "room_id": room_id, "player_id": "player-uuid-123"})


def test_annex_room_charges_once(client):
    base_id = create_base(client).get_json()["base_id"]
    wood_before = resource_service.get_stock(base_id)["wood-01"]

    res = annex(client, base_id)
    assert res.status_code == 201
    assert res.get_json()["cost_paid"] == [{"item_id": "wood-01", "count": 10}]
    annex(client, base_id)  # retry with the same event_id
    assert resource_service.get_stock(base_id)["wood-01"] == wood_before - 10

    rooms = client.get(f"/api/bases/{base_id}/rooms", headers=PLAYER).get_json()
    assert [r["room_id"] for r in rooms] == ["faf-cab", "lab-a1"]


def test_annex_errors(client):
    base_id = create_base(client).get_json()["base_id"]
    assert annex(client, base_id, "hallway-3", "e1").status_code == 409  # not cleared
    assert annex(client, base_id, "faf-cab", "e2").status_code == 409    # already ours

    resource_service.get_stock(base_id)["wood-01"] = 6
    res = annex(client, base_id, "lab-a1", "e3")
    assert res.status_code == 402
    assert res.get_json()["missing"] == [{"item_id": "wood-01", "count": 4}]


def test_homeroom_cannot_be_removed(client):
    base_id = create_base(client).get_json()["base_id"]
    annex(client, base_id)
    assert client.delete(f"/api/bases/{base_id}/rooms/faf-cab", headers=SERVICE).status_code == 409
    res = client.delete(f"/api/bases/{base_id}/rooms/lab-a1", headers=SERVICE)
    assert res.get_json() == {"room_id": "lab-a1", "removed": True}


# --- Barricades -------------------------------------------------------------
def test_barricade_lifecycle(client):
    base_id = create_base(client).get_json()["base_id"]
    annex(client, base_id)
    url = f"/api/bases/{base_id}/rooms/lab-a1/barricade"

    client.post(url, headers=SERVICE, json={"event_id": "b1", "player_id": "player-uuid-123"})
    res = client.post(url, headers=SERVICE, json={"event_id": "b2", "player_id": "player-uuid-123"})
    assert res.get_json() == {
        "room_id": "lab-a1", "barricade_level": 2, "hp": 150, "max_hp": 150,
        "cost_paid": [{"item_id": "wood-01", "count": 5}, {"item_id": "metal-01", "count": 2}]}
    assert world_service.barricade_levels["lab-a1"] == 2

    res = client.patch(url, headers=SERVICE, json={"event_id": "a1", "operation": "damage", "amount": 30})
    assert res.get_json() == {"room_id": "lab-a1", "barricade_level": 2,
                              "hp": 120, "max_hp": 150, "destroyed": False}
    # Same attack event delivered twice must not hit twice.
    client.patch(url, headers=SERVICE, json={"event_id": "a1", "operation": "damage", "amount": 30})
    assert client.get(url, headers=PLAYER).get_json()["hp"] == 120

    res = client.patch(url, headers=SERVICE, json={"event_id": "r1", "operation": "repair", "amount": 100})
    assert res.get_json()["hp"] == 150  # capped at max_hp

    res = client.delete(url, headers=SERVICE)
    assert res.get_json() == {"room_id": "lab-a1", "barricade_level": 0, "destroyed": True}
    assert world_service.barricade_levels["lab-a1"] == 0


def test_barricade_with_kit_and_insufficient_resources(client):
    base_id = create_base(client).get_json()["base_id"]
    url = f"/api/bases/{base_id}/rooms/faf-cab/barricade"

    res = client.post(url, headers=SERVICE, json={
        "event_id": "k1", "player_id": "player-uuid-123", "kit_item_id": "BARRICADE_KIT"})
    assert res.status_code == 200
    assert res.get_json()["cost_paid"] == [{"item_id": "BARRICADE_KIT", "count": 1}]

    resource_service.get_stock(base_id)["metal-01"] = 0
    res = client.post(url, headers=SERVICE, json={"event_id": "k2", "player_id": "player-uuid-123"})
    assert res.status_code == 402
    assert res.get_json() == {"error": "Insufficient resources.",
                              "missing": [{"item_id": "metal-01", "count": 2}]}


def test_damage_to_zero_destroys(client):
    base_id = create_base(client).get_json()["base_id"]
    url = f"/api/bases/{base_id}/rooms/faf-cab/barricade"
    client.post(url, headers=SERVICE, json={"event_id": "b1", "player_id": "player-uuid-123"})
    res = client.patch(url, headers=SERVICE, json={"event_id": "a1", "operation": "damage", "amount": 999})
    assert res.get_json()["destroyed"] is True
    assert res.get_json()["barricade_level"] == 0


def test_health_needs_no_token(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.get_json() == {"status": "UP"}

    