# Contract — transaction-service

Base: `/api/v1/transactions`

## POST /transfers  (JWT CUSTOMER)

Headers: `Idempotency-Key: <uuid>`

```json
{
  "fromAccountId": "uuid",
  "toAccountNumber": "1098765432",
  "amount": 50000,
  "description": "Tra no cafe"
}
```

Response 200/201:
```json
{
  "transactionId": "uuid",
  "status": "COMPLETED",
  "fromAccountId": "...",
  "toAccountId": "...",
  "amount": 50000,
  "currency": "VND",
  "createdAt": "..."
}
```

Possible status in response: COMPLETED | FAILED | COMPENSATED  
(MVP sync saga in request thread — simpler demo; timeout long enough)

Errors: `INSUFFICIENT_BALANCE`, `ACCOUNT_FROZEN`, `ACCOUNT_NOT_FOUND`, `SAME_ACCOUNT`, `INVALID_AMOUNT`, `IDEMPOTENCY_CONFLICT`

## GET /transfers  (JWT)

My history pageable

## GET /transfers/{id}  (JWT)

Own or ADMIN

## Admin

### GET /admin/transfers  pageable filter status
### GET /admin/audit-logs

## Events (Kafka payload)

### TransactionCompletedEvent

```json
{
  "eventId": "uuid",
  "eventType": "TRANSACTION_COMPLETED",
  "occurredAt": "ISO-8601",
  "correlationId": "uuid",
  "data": {
    "transactionId": "uuid",
    "userId": "uuid",
    "fromAccountId": "uuid",
    "toAccountId": "uuid",
    "amount": 50000,
    "currency": "VND",
    "description": "..."
  }
}
```

### TransactionFailedEvent

Similar + `failureReason`
