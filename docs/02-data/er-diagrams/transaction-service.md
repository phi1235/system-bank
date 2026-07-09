# ER — transaction-service (`bank_transaction`)

```mermaid
erDiagram
  transfer_orders ||--o{ saga_step_logs : has
  transfer_orders ||--o{ outbox_events : produces
  audit_logs

  transfer_orders {
    uuid id PK
    string idempotency_key UK
    uuid user_id
    uuid from_account_id
    uuid to_account_id
    string to_account_number
    decimal amount
    string currency
    string description
    string status
    string failure_reason
    string debit_entry_ref
    string credit_entry_ref
    timestamptz created_at
    timestamptz updated_at
  }

  saga_step_logs {
    uuid id PK
    uuid transfer_id FK
    string step
    string status
    text detail
    timestamptz created_at
  }

  outbox_events {
    uuid id PK
    string aggregate_type
    uuid aggregate_id
    string event_type
    jsonb payload
    timestamptz created_at
    timestamptz published_at
  }

  audit_logs {
    uuid id PK
    uuid actor_user_id
    string action
    string resource_type
    string resource_id
    string ip
    jsonb metadata
    timestamptz created_at
  }
```

Full SQL: implement trong Flyway theo columns trên.  
Status values: `PENDING|DEBITED|COMPLETED|FAILED|COMPENSATING|COMPENSATED`  
See `docs/01-architecture/saga-transfer.md`.
