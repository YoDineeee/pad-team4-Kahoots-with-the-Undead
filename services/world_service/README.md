# World Service

## Responsibility
Campus map, rooms, resource nodes, zombie spawn points, unlocks wings.

## What it owns
- Campus geography, rooms, nodes, spawn points, wing unlocks

## What it does not own
- Player state, game sessions, base builds

## Communication
- REST; receives ExamPassed to unlock wings.

## API Endpoints
```
GET  /world/map                -> 200 { "rooms","nodes","spawns" }
GET  /world/rooms/:id          -> 200 { "roomId","name","wing","unlocked","zombieSpawnPoints" }
POST /world/rooms/:id/unlock   { "playerId","actionId" } -> 200 { "status","room" }
GET  /world/nodes/:id          -> 200 { "nodeId","resource","amount","roomId" }
```

## Data Management
- Database: MongoDB
