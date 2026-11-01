# Base Service

## Responsibility
Responsible for the player's survival base, initially represented by the FAF Cab room. Manages base upgrades, barricades, facilities and defensive improvements.

## What it owns
- Base state (persistent, separate from World Service geography)
- Upgrades to rooms, facilities, storage capacity
- Barricades and defensive improvements
- Decorations/upgrades to the homeroom
- Kiki interactions and rewards

## What it does not own
- Campus geography (owned by World Service)
- Resource storage/quantities (owned by Resource Service)
- Inventory management (owned by Player Service)
- Game session logic (owned by Game Service)

## Features
Players spend resources obtained through the Resource Service to:
- Reinforce rooms
- Construct barricades
- Improve facilities
- Unlock storage capacity
- Decorate/upgrade the homeroom, etc.

The base can also contain Kiki, whose interactions may provide random booster objects or other rewards.

## Separation of Concerns
- **World Service** owns the campus geography
- **Base Service** owns what the players have built or changed within that geography

## Communication
- **Resource Service**: Validates and applies resource consumption for barricading, upgrading, crafting consumption, feeding Kiki (via idempotent operations with `actionId`)
- **Player Service**: May receive rewards/items from Kiki interactions
- **Game Service**: Coordinates timed actions (scavenging) - Game manages timing, Resource Service owns resource application

## API Endpoints

```
GET /base/:playerId
Response 200: {
  "playerId": "uuid",
  "rooms": [{"roomId": "string", "level": "int", "barricaded": "bool", "upgrades": []}],
  "facilities": [{"type": "string", "level": "int"}],
  "storageCapacity": "int",
  "kiki": {"present": true, "happiness": "int"}
}

POST /base/upgrade
Request: { "playerId": "uuid", "actionId": "uuid", "roomId": "string", "upgradeType": "string" }
Response 200: { "status": "upgraded", "cost": {...}, "newState": {...} }
Response 409: { "error": "insufficient resources" }

POST /base/barricade
Request: { "playerId": "uuid", "actionId": "uuid", "roomId": "string" }
Response 200: { "status": "barricaded", "resourcesConsumed": {...} }

POST /base/kiki/interact
Request: { "playerId": "uuid", "actionId": "uuid" }
Response 200: { "reward": {...}, "type": "booster|item" }
```

## Data Management
- Database per service
- Tracks persistent base state
- All resource changes go through Resource Service with idempotency (`actionId`)
