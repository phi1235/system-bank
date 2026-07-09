# Saga Pattern — Chuyển khoản nội bộ

> Đây là **điểm wow phỏng vấn**. Implement đúng file này, không tự bịa biến thể.

## Business flow (happy path)

```
1. Customer POST /api/v1/transactions/transfers
   body: { fromAccountId, toAccountNumber, amount, description, currency=VND }
   header: Idempotency-Key

2. transaction-service:
   a. Validate idempotency (return existing if replay)
   b. Load/validate from account (Feign) — owner == current user, ACTIVE, balance check soft
   c. Resolve toAccount by number (Feign)
   d. Create TransferOrder status=PENDING + Outbox empty
   e. Start saga
```

## Orchestration steps

```
                    ┌─────────────────────┐
                    │  TransferOrder      │
                    │  status=PENDING     │
                    └──────────┬──────────┘
                               ▼
              STEP1 RESERVE/DEBIT SOURCE
              Feign: account debit (or reserve then capture)
              success → status=DEBITED
                               ▼
              STEP2 CREDIT DESTINATION
              Feign: account credit
              success → status=COMPLETED
                               ▼
              Write Outbox: TransactionCompletedEvent
              Poller → Kafka bank.transaction.completed
```

## Compensation (failure)

| Fail at | Compensate | Final status |
|---------|------------|--------------|
| After debit, credit fails | `release/refund` source | `COMPENSATED` |
| Debit fails (insufficient) | no-op | `FAILED` |
| Validation fail | no-op | `FAILED` |
| Duplicate idempotency | return previous | (no new saga) |

```
DEBITED + credit fail → compensate credit source → COMPENSATED
Publish bank.transaction.failed (optional) + notify user
```

## Account balance model (MVP đơn giản)

**Option A (chọn cho MVP — dễ demo compensate):**

- `POST /internal/accounts/{id}/debit` — trừ balance ngay nếu đủ tiền; return `ledgerEntryId`
- `POST /internal/accounts/{id}/credit` — cộng balance
- `POST /internal/accounts/{id}/credit` với reason=COMPENSATION cho reverse debit  
  (hoặc endpoint `POST .../refund` mirror amount)

**Option B (hold/reserve):** phức tạp hơn — **không** làm MVP trừ khi dư thời gian.

→ **Chốt Option A** (ADR-005).

## State machine

```
PENDING → DEBITED → COMPLETED
   │         │
   │         └→ COMPENSATING → COMPENSATED
   └→ FAILED
```

Enum: `TransferStatus`

## Tables (transaction DB)

### transfer_order
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | transactionId |
| idempotency_key | VARCHAR UNIQUE | per user |
| user_id | UUID | |
| from_account_id | UUID | |
| to_account_id | UUID | |
| amount | DECIMAL(19,2) | > 0 |
| currency | VARCHAR(3) | VND |
| description | VARCHAR(255) | |
| status | VARCHAR | enum |
| failure_reason | VARCHAR | |
| debit_entry_ref | VARCHAR | từ account-svc |
| credit_entry_ref | VARCHAR | |
| created_at / updated_at | TIMESTAMPTZ | |

### saga_step_log (optional nhưng tốt cho demo)
| Column | Type |
|--------|------|
| id | UUID |
| transfer_id | UUID |
| step | VARCHAR |
| status | VARCHAR |
| detail | TEXT |
| created_at | TIMESTAMPTZ |

### outbox_event
| Column | Type |
|--------|------|
| id | UUID |
| aggregate_type | VARCHAR |
| aggregate_id | UUID |
| event_type | VARCHAR |
| payload | JSONB |
| created_at | TIMESTAMPTZ |
| published_at | TIMESTAMPTZ NULL |

Outbox poller: mỗi 1s SELECT unpublished LIMIT 100 → publish → set published_at.

## Idempotency

- Unique `(user_id, idempotency_key)` hoặc global key string unique
- Same key + same body → 200 return existing
- Same key + different body → 409 CONFLICT

## Concurrent transfer

- Account-service debit: `UPDATE accounts SET balance = balance - :amt WHERE id=:id AND balance >= :amt AND status='ACTIVE'`
- `rowCount==0` → insufficient / locked → saga FAILED

## Sequence (happy)

```
FE → GW → TX
TX → ACC debit(from)
TX → ACC credit(to)
TX commit order COMPLETED + outbox
Outbox → Kafka
NOTI consume → mock email log
```

## Interview talking points (ghi nhớ)

1. Vì sao không 2PC: coupling, lock lâu, availability
2. Orchestration vs choreography: orchestration dễ debug cho 3 steps
3. Outbox: dual-write problem DB vs Kafka
4. At-least-once + idempotent consumer
5. Idempotency key chống double-click chuyển tiền
