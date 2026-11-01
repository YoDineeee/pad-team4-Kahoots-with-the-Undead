# Game Service

## Responsibility
Sessions/lobbies, day/night cycle, timers, timed actions (WebSockets), zombie behavior during a cycle. Does not own map or inventory.

## What it owns
- Game sessions, lobbies, timers, cycle state
- Timed gathering actions (WebSockets)

## What it does not own
- Map/world, inventory, resources storage

## Communication
- REST + WebSockets; calls World, Zombie, Resource, Exam services.

## API Endpoints
```
POST /games/sessions            { "hostId","maxPlayers" } -> 201 { "sessionId","code" }
POST /games/sessions/:id/join   { "playerId" } -> 200 { "sessionId","players" }
POST /games/sessions/:id/start  -> 200 { "status","cycle" }
GET  /games/sessions/:id        -> 200 { "sessionId","state","cycle","timer" }
POST /games/sessions/:id/action  { "playerId","actionId","action","nodeId","duration" } -> 202 { "status","endsAt" }
```

## WebSocket Messages
```json
{ "type": "day_start|night_start|zombie_spawn|timer_update|action_complete", "payload": {...} }
```

## Data Management
- Database: Redis (session state)
