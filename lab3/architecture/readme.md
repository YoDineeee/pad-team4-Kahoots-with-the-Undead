                     ┌───────────────┐
        Client ──────┤   gateway     │ :8080   (auth  + RR LB + WS proxy + CB)
                     └──────┬────────┘
                            │  GET /services every 2 s
                     ┌──────▼──────────┐
                     │ service-discovery│ :8761  (registry + health poller + alerts + quarantine)
                     └──────┬──────────┘
      ┌─────────────┬───────┼────────┬─────────────┬──────────────┬────────────┬────────────┐
 player-service game-service zombie-service resource-service base-service crafting-service
   (Java)         (Java)        (Java)           (Java)         (Python)      (Python)
      │             │             │                 │               │              │
      └── every outbound call goes through ServiceClient / call() ──┴── round-robin + circuit breaker


      