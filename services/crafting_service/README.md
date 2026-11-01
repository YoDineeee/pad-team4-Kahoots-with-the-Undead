# Crafting Service

## Responsibility
Allows players to combine resources into useful survival equipment. Recipes are defined by the service and can require resources obtained from different areas of the university.

## What it owns
- Recipe definitions and availability rules
- Crafting validation logic
- Atomic crafting operations
- Recipe unlock conditions

## What it does not own
- Resource quantities/storage (owned by Resource Service)
- Player inventory (owned by Player Service)
- Base state (owned by Base Service)

## Example Recipes
- Wood + Metal → Barricade Kit
- Paper + Metal → Improvised Weapon
- Food + Chemicals → Energy Booster
- Metal + Electronics → Zombie Detector
- Paper + Wood → Exam Cheat Sheet

## Features
- Validates that the player has the required materials
- Performs the crafting operation atomically
- Transfers crafted objects to the player's inventory through the Player Service
- Some recipes may only become available after passing particular exams, reaching a certain level, unlocking a university wing or discovering a special resource

## Communication
- **Resource Service**: Consume required resources for crafting (idempotent with `actionId`)
- **Player Service**: Transfer crafted items to player's inventory
- **Exam Service/World Service/Player Service**: Check unlock conditions (passed exams, level, unlocked wings)

## API Endpoints

```
GET /craft/recipes
Query: ?playerId=uuid
Response 200: {
  "recipes": [
    {"recipeId": "uuid", "name": "string", "ingredients": [{"resource": "string", "amount": "int"}], "result": {...}, "unlocked": true}
  ]
}

GET /craft/recipes/:recipeId
Response 200: { "recipeId": "uuid", "name": "string", "ingredients": [...], "result": {...}, "requirements": {...} }

POST /craft/recipe/:recipeId
Request: { "playerId": "uuid", "actionId": "uuid" }
Response 200: { "status": "crafted", "item": {...}, "inventoryUpdated": true }
Response 400: { "error": "missing materials" }
Response 403: { "error": "recipe locked" }
Response 409: { "error": "action already processed" }

// Atomic operation: validates materials, consumes resources via Resource Service, transfers item via Player Service
```

## Events
- **ActionCompleted**: Emitted on successful craft with `actionId` for idempotency

## Data Management
- Database per service
- Recipe definitions stored in Crafting Service DB
- Atomic transactions across service calls or saga pattern for distributed atomicity
- Unlock conditions evaluated dynamically based on player state from other services
