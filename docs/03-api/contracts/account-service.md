# Contract — account-service

Base: `/api/v1/accounts`

## POST /  (JWT CUSTOMER) — open payment account

```json
{ "accountType": "PAYMENT" }
```

Rules MVP:
- Max 3 accounts / user
- Initial balance: **10_000_000 VND** demo seed (config flag) **hoặc** 0 + admin topup
- **Chốt:** initial balance `1_000_000` VND for demo ease (ADR-008)

Response 201: account object

## GET /  (JWT)

List my accounts

## GET /{id}  (JWT)

Own only (ADMIN can any)

## Admin

### POST /{id}/freeze  (ADMIN)
### POST /{id}/unfreeze  (ADMIN)

## Internal (no GW)

### GET /internal/accounts/{id}
### GET /internal/accounts/by-number/{accountNumber}

```json
{
  "id": "uuid",
  "userId": "uuid",
  "accountNumber": "0123456789",
  "balance": 1000000.00,
  "status": "ACTIVE",
  "currency": "VND"
}
```

### POST /internal/accounts/{id}/debit

```json
{
  "amount": 100000,
  "referenceId": "transfer-uuid",
  "description": "Transfer debit",
  "commandId": "uuid"
}
```

Response: `{ "ledgerEntryId": "uuid", "balanceAfter": ... }`  
Errors: `INSUFFICIENT_BALANCE`, `ACCOUNT_FROZEN`, `ACCOUNT_NOT_FOUND`  
Idempotent by `commandId` (store processed commands table optional — MVP: unique on ledger referenceId+type)

### POST /internal/accounts/{id}/credit

Same shape; for compensation reverse.

### Account number generation

10 digits, unique, prefix `10` + random.
