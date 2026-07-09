# PHASE 04 — transaction Saga + Outbox

## Read

- **Toàn bộ** `docs/01-architecture/saga-transfer.md`
- transaction IMPLEMENT + contract + ER

## Checklist

- [x] transfer API + idempotency (`Idempotency-Key`)
- [x] saga orchestrator (DEBIT → CREDIT)
- [x] compensate path (credit source refund)
- [x] outbox poller → Kafka topics
- [x] audit log TRANSFER_CREATE
- [x] Feign + Resilience4j circuit breaker enabled
- [x] `SAGA_FAIL_CREDIT=true` → COMPENSATED

## Status

**DONE** (2026-07-09)  
- Happy: A→B 50_000 → COMPLETED, balances 950k / 1.05M  
- Idempotent replay same key  
- Inject fail-credit → **COMPENSATED**, source balance restored 1_000_000  
- Host port: `18084`

## Exit

Happy transfer COMPLETED; forced fail → COMPENSATED + balance restored. ✅
