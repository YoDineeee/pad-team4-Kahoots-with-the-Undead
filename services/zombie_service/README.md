# Zombie Service

## Responsibility
Zombie types (Professor, Tourist, + custom), stats.

## What it owns
- Zombie type definitions and stats

## What it does not own
- Spawn logic coordination, game sessions

## Communication
- REST.

## API Endpoints
```
GET  /zombies/types            -> 200 { "types": [{"typeId","name","stats"}] }
GET  /zombies/types/:id        -> 200 { "typeId","name","stats" }
POST /zombies/types           { "name","stats" } -> 201 { "typeId" }
```

## Data Management
- Database: MongoDB
