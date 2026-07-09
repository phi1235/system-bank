# PHASE 05 — notification + E2E backend

## Read

- notification IMPLEMENT
- `docs/03-api/contracts/notification-service.md`
- `docs/02-data/er-diagrams/notification-service.md`

## Checklist

- [x] notification-service consumer idempotent (`processed_events` + skip duplicate)
- [x] Kafka listeners: `bank.transaction.completed` + `bank.transaction.failed`
- [x] Mock email/SMS log (`MOCK_EMAIL` / `MOCK_SMS`)
- [x] `notification_logs` persistence status `SENT`
- [x] Internal debug API `GET /internal/notifications` + `X-Internal-Api-Key`
- [x] Docker compose service host port `18085` → `8085`
- [x] E2E: register → profile → account → transfer → notification log
- [x] Fix integration bugs (empty `spring.kafka.listener` YAML bind crash)

## Status

**DONE** (2026-07-09)

### Verified

1. **Healthy:** `bank-notification` UP on `:18085`, Eureka `NOTIFICATION-SERVICE`
2. **Replay history:** after start with `auto-offset-reset: earliest`, consumed prior Phase 4 events (COMPLETED + COMPENSATED/FAILED)
3. **Fresh E2E:**
   - register/login customer
   - create profile
   - open 2 accounts (demo balance 1_000_000)
   - transfer `77777` VND → status **COMPLETED**
   - notification: template `TRANSFER_COMPLETED`, channel `EMAIL`, status `SENT`
   - log line: `MOCK_EMAIL ... Phase5 E2E notify` + `Notification SENT`

### Internal API sample

```bash
curl -s http://localhost:18085/internal/notifications \
  -H "X-Internal-Api-Key: dev-internal-api-key-change-me" | jq .
```

## Exit

Full backend demo without UI. ✅

Next: **Phase 6** — Angular FE (`docs/08-phases/PHASE-06-frontend.md`)
