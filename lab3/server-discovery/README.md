# pad-service-discovery

Registry + health poller + alert source for PAD Labs 2–3.

## Endpoints
- `POST /register`          `{name,host,port}` — called by every service at boot
- `POST /deregister`        `{name,host,port}` — graceful shutdown
- `POST /quarantine`        `{name,host,port,reason}` — called by CB on trip (Grade 9)
- `GET  /services/:name`    `[{host,port}]` — healthy instances only
- `GET  /services`          `{name:[{host,port}]}`
- `GET  /debug`             full state (healthy, quarantined, active, capacity, lastSeen)
- `GET  /health`

## Polling & alerts
- Every `POLL_MS` (default 5 s), probes `GET <host>:<port>/health`.
- 3 fails → quarantine + notify.
- 3 consecutive healthy probes → un-quarantine.
- `active/capacity ≥ CRITICAL_LOAD` (0.8) → notify.

## Env
| var | default | purpose |
|-----|---------|---------|
| PORT | 8761 | |
| POLL_MS | 5000 | |
| CRITICAL_LOAD | 0.8 | alert threshold |
| DISCORD_WEBHOOK_URL | — | Grade 8 |
| TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID | — | Grade 8 |
| ALERT_EMAIL_WEBHOOK | — | Grade 8 |
