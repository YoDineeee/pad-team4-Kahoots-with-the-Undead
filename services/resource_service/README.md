# Resource Service

## Responsibility
Resource quantities, idempotent apply/consume.

## What it owns
- Resource quantities per player/node
- Idempotent apply/consume via actionId

## What it does not own
- Crafting recipes, player inventory

## Communication
- REST; idempotent operations.

## API Endpoints
```
POST /resources/apply          { "playerId","actionId","resource","amount" } -> 200 { "status","newBalance" } | 409 { "error" }
POST /resources/consume        { "playerId","actionId","resource","amount" } -> 200 { "status","newBalance" } | 409 { "error" }
GET  /resources/:playerId/balance -> 200 { "balances" }
```

## Events
- ResourceApplied: { "eventType","playerId","actionId","resource","amount","newBalance" }

## Data Management
- Database: PostgreSQL
