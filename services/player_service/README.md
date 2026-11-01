# Player Service

## Responsibility
Registration, authentication, profiles, friends, XP/levels, inventory.

## What it owns
- Player accounts, credentials, profiles
- XP, levels, friends
- Inventory (items granted by other services)

## What it does not own
- Game sessions, exams, world state, resources, base

## Communication
- REST endpoints; receives item grants from Crafting Service; receives rewards from Exam Service.

## API Endpoints
```
POST /players/register  { "username","email","password" } -> 201 { "playerId","token" }
POST /players/login     { "email","password" } -> 200 { "playerId","token" }
GET  /players/:id       -> 200 { "playerId","username","level","xp","inventory" }
PUT  /players/:id/xp    { "amount","reason" } -> 200 { "level","xp" }
GET  /players/:id/friends -> 200 { "friends" }
POST /players/:id/friends { "friendId" } -> 201 { "status" }
POST /players/:id/inventory { "itemId","actionId" } -> 200 { "status","inventory" }
```

## Data Management
- Database: PostgreSQL
- Owns player data; other services access via API only.
