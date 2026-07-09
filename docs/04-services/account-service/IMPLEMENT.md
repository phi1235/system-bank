# IMPLEMENT — account-service

## Goal

Accounts, balances, freeze, **atomic debit/credit** for saga.

## Path

`backend/account-service/`

## Read first

- contracts/account-service.md
- er-diagrams/account-service.md
- saga-transfer.md (debit semantics)

## Checklist

- [ ] Flyway accounts + ledger_entries
- [ ] Open account (max 3), generate account_number, initial balance ADR-008
- [ ] List/get my accounts
- [ ] Admin freeze/unfreeze
- [ ] Internal get by id / by number
- [ ] Internal debit (conditional UPDATE) + ledger
- [ ] Internal credit + ledger
- [ ] Idempotency: unique (account_id, reference_id, entry_type) prevent double debit
- [ ] Feign not required outbound (optional customer exists check)
- [ ] Tests: concurrent debit insufficient, freeze reject
- [ ] Eureka, Swagger, Docker

## Critical code path

Debit must be single atomic SQL — interview focus.
