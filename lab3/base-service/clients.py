"""Clients for the services the Base Service depends on.

In a real deployment every method below would be an HTTP call, for example:

    requests.patch(f"{RESOURCE_URL}/api/resources/entities/base/{base_id}",
                   json={"operation": "remove", "items": cost},
                   headers={"Authorization": f"Bearer {SERVICE_JWT}"})

For the lab we use small *in-memory fakes* so this service runs on its own.
They expose the same methods a real client would, so replacing them later
does not change a single line in app.py.
"""
import logging

log = logging.getLogger("base-service.clients")



class FakeResourceService:
    """Stands in for the Resource Service (owns wood / metal / food stock)."""

    STARTING_STOCK = {"wood-01": 50, "metal-01": 20, "food-01": 10}

    def __init__(self):
        self.stock = {}  # base_id -> {item_id: count}

    def get_stock(self, base_id):
        """GET /api/resources/entities/base/{base_id}"""
        return self.stock.setdefault(base_id, dict(self.STARTING_STOCK))

    def missing_items(self, base_id, cost):
        """Pre-check: which items (and how many) are lacking to pay `cost`."""
        stock = self.get_stock(base_id)
        missing = []
        for item in cost:
            have = stock.get(item["item_id"], 0)
            if have < item["count"]:
                missing.append({"item_id": item["item_id"], "count": item["count"] - have})
        return missing

    def remove(self, base_id, cost):
        """PATCH /api/resources/entities/base/{base_id}  {"operation": "remove"}"""
        log.info("[MOCK Resource Service] PATCH /api/resources/entities/base/%s remove %s", base_id, cost)
        stock = self.get_stock(base_id)
        for item in cost:
            stock[item["item_id"]] -= item["count"]


class FakeWorldService:
    """Stands in for the World Service (owns the campus map)."""

    def __init__(self):
        # room_id -> is the room cleared of zombies?
        self.rooms = {"faf-cab": True, "lab-a1": True, "lab-a2": True, "hallway-3": False}
        self.barricade_levels = {}  # what we reported, room_id -> level

    def get_room(self, room_id):
        """GET /api/world/rooms/{room_id}  -> None means 404."""
        if room_id not in self.rooms:
            return None
        return {"room_id": room_id, "cleared": self.rooms[room_id]}

    def report_barricade(self, room_id, level):
        """POST /api/world/rooms/{room_id}/barricades"""
        log.info("[MOCK World Service] POST /api/world/rooms/%s/barricades level=%s", room_id, level)
        self.barricade_levels[room_id] = level


class FakePlayerService:
    """Stands in for the Player Service (owns player inventories)."""

    def __init__(self):
        self.inventory = {"player-uuid-123": {"BARRICADE_KIT": 1}}

    def take_item(self, player_id, item_code):
        """POST /api/players/{player_id}/inventory  {"delta": -1}
        Returns False if the player does not own the item."""
        log.info("[MOCK Player Service] POST /api/players/%s/inventory %s delta=-1", player_id, item_code)
        items = self.inventory.get(player_id, {})
        if items.get(item_code, 0) < 1:
            return False
        items[item_code] -= 1
        return True


# One shared instance of each, imported by app.py (and reset by the tests).
resource_service = FakeResourceService()
world_service = FakeWorldService()
player_service = FakePlayerService()

