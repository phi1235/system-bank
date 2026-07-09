# IMPLEMENT — transaction-service

## Goal

**Saga orchestration + Outbox + transfer API + audit.** Core wow module.

## Path

`backend/transaction-service/`

## Read first (BẮT BUỘC đủ bộ)

1. `docs/01-architecture/saga-transfer.md`
2. `docs/01-architecture/communication.md`
3. `docs/03-api/contracts/transaction-service.md`
4. `docs/02-data/er-diagrams/transaction-service.md`

## Components

| Class | Responsibility |
|-------|----------------|
| TransferController | REST |
| TransferService | idempotency + start |
| TransferSagaOrchestrator | steps debit/credit/compensate |
| AccountClient (Feign) | internal account APIs |
| OutboxService + OutboxPoller | publish Kafka |
| AuditService | write audit_log |
| KafkaProducerConfig | |

## Checklist

- [ ] Flyway transfer_orders, saga_step_logs, outbox_events, audit_logs
- [ ] POST /transfers with Idempotency-Key
- [ ] Saga happy path COMPLETED
- [ ] Simulate credit fail (profile/test flag) → COMPENSATED
- [ ] Outbox poller @Scheduled
- [ ] Publish completed/failed events
- [ ] Resilience4j on Feign
- [ ] GET history
- [ ] Admin list + audit
- [ ] Integration test Testcontainers optional; unit test saga state transitions
- [ ] Docker, Eureka, Swagger

## Anti-patterns

- ❌ Publish Kafka inside @Transactional before commit without outbox
- ❌ Call account DB directly
- ❌ Skip idempotency

## Demo failure inject

`application.yml`:
```yaml
bank.saga.fail-credit: false
```
Set true in test compose profile to force compensate.
