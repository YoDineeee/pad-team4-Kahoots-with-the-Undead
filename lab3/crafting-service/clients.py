"""Clients for the services the Crafting Service depends on.

In a real deployment each method is an HTTP call (see the docstrings for the
endpoint). For the lab these are *in-memory fakes* with some seed data, so
the service runs on its own and you can still exercise every error path.
"""
import logging

log = logging.getLogger("crafting-service.clients")



class FakeResourceService:
    """Owns the players' raw materials (wood, metal, paper...)."""

    def __init__(self):
        self.stock = {
            "player-uuid-123": {"wood-01": 20, "metal-01": 10, "paper-01": 0,
                                "food-01": 5, "chemicals-01": 2, "electronics-01": 0},
        }

    def get_stock(self, player_id):
        """GET /api/resources/entities/player/{player_id}"""
        return self.stock.setdefault(player_id, {})

    def missing_items(self, player_id, items):
        stock = self.get_stock(player_id)
        missing = []
        for item in items:
            have = stock.get(item["item_id"], 0)
            if have < item["count"]:
                missing.append({"item_id": item["item_id"], "count": item["count"] - have})
        return missing

    def remove(self, player_id, items):
        """PATCH /api/resources/entities/player/{player_id}  {"operation": "remove"}"""
        log.info("[MOCK Resource Service] PATCH /api/resources/entities/player/%s remove %s", player_id, items)
        stock = self.get_stock(player_id)
        for item in items:
            stock[item["item_id"]] -= item["count"]

    def add(self, player_id, items):
        """PATCH /api/resources/entities/player/{player_id}  {"operation": "add"}
        Used to refund materials when a craft fails or is cancelled."""
        log.info("[MOCK Resource Service] PATCH /api/resources/entities/player/%s add %s", player_id, items)
        stock = self.get_stock(player_id)
        for item in items:
            stock[item["item_id"]] = stock.get(item["item_id"], 0) + item["count"]


class PlayerServiceError(Exception):
    """Raised when the Player Service call fails (network error, 5xx...)."""


class FakePlayerService:
    """Owns player levels and inventories."""

    def __init__(self):
        self.levels = {"player-uuid-123": 3}
        self.inventory = {}       # player_id -> {code: quantity}
        self.fail_next_credit = False  # set True in a test to simulate an outage

    def get_level(self, player_id):
        """GET /api/players/{player_id}/xp"""
        return self.levels.get(player_id, 1)

    def credit_item(self, player_id, code, quantity):
        """POST /api/players/{player_id}/inventory -> the new item's id."""
        log.info("[MOCK Player Service] POST /api/players/%s/inventory %s x%s", player_id, code, quantity)
        if self.fail_next_credit:
            self.fail_next_credit = False
            raise PlayerServiceError("Player Service unavailable")
        items = self.inventory.setdefault(player_id, {})
        items[code] = items.get(code, 0) + quantity
        return f"item-{code.lower()}-{items[code]}"


class FakeExamService:
    def __init__(self):
        self.passed = {"player-uuid-123": set()}  # player_id -> {exam_id}

    def has_passed(self, player_id, exam_id):
        """GET /api/players/{player_id}/exams"""
        return exam_id in self.passed.get(player_id, set())


class FakeWorldService:
    def __init__(self):
        self.unlocked_zones = {"zone-main-hall"}

    def is_zone_unlocked(self, zone_id):
        """GET /api/world/zones/{zone_id}"""
        return zone_id in self.unlocked_zones


class FakeBaseService:
    def __init__(self):
        self.facilities = {"base-uuid-001": {"workbench"}}

    def has_facility(self, base_id, facility):
        """GET /api/bases/{base_id}/facilities"""
        return facility in self.facilities.get(base_id, set())


resource_service = FakeResourceService()
player_service = FakePlayerService()
exam_service = FakeExamService()
world_service = FakeWorldService()
base_service = FakeBaseService()

