# Architecture

Paste this into the CPR README (GitHub renders Mermaid blocks automatically).

```mermaid
flowchart LR
    client["Client<br/>(Postman / frontend)"]

    subgraph compose["Docker Compose network"]
        gateway["Gateway<br/>JavaScript · Express<br/>:8080<br/>round robin + circuit breaker"]
        sd["Service Discovery<br/>JavaScript · Express<br/>:8761<br/>registry"]
        player["Player Service<br/>Java · Spring Boot<br/>:8081"]
        game["Game Service<br/>Java · Spring Boot<br/>:8082"]
        zombie["Zombie Service ×3<br/>Java · Spring Boot<br/>:8081"]
        resource["Resource Service ×3<br/>Java · Spring Boot<br/>:8082"]
        base["Base Service<br/>Python · Flask<br/>:5007"]
        crafting["Crafting Service<br/>Python · Flask<br/>:5008"]
        zdb[("zombie-db<br/>PostgreSQL")]
        rdb[("resource-db<br/>PostgreSQL")]
    end

    world["World Service<br/>(mocked)"]

    client -- "REST /api/**" --> gateway
    gateway -- "/api/players, /api/trades" --> player
    gateway -- "/api/game, /api/lobbies, /api/sessions" --> game
    gateway -- "/api/zombies" --> zombie
    gateway -- "/api/resources" --> resource
    gateway -- "/api/bases" --> base
    gateway -- "/api/crafting" --> crafting
    gateway -- "GET /services every 3 s" --> sd
    player & game & zombie & resource & base & crafting -. "POST /register every 10 s" .-> sd
    zombie --> zdb
    resource --> rdb
    game -. "XP on session end (mocked)" .-> player
    game -. "map for session (mocked)" .-> world
    game -. "action rewards (mocked)" .-> resource
    crafting -. "materials (mocked)" .-> resource
    base -. "annex / barricade cost (mocked)" .-> resource
```

- Solid arrows: implemented HTTP calls. Dotted arrows from game/base/crafting: service-to-service calls that are mocked for now.
- Lab 3: every service registers with the Service Discovery on startup (`POST /register`) and every 10 s (heartbeat). The gateway balances over the registered instances (round robin); its circuit breaker logs an ERROR when one instance fails 3 times within `UPSTREAM_TIMEOUT_MS * 3.5`.
- zombie-service and resource-service run 3 replicas each (state in PostgreSQL); the in-memory services run one instance.
- Only the gateway publishes a port to the host; the services and databases are reachable only inside the compose network.
- Database per service: zombie-service and resource-service each own a PostgreSQL container and volume.
- The Game Service validates JWTs issued by the Player Service with the shared `JWT_SECRET`.

