# IMPLEMENT — customer-portal features

## Read

- `docs/05-frontend/ARCHITECTURE.md`
- API contracts auth, customer, account, transaction

## Checklist screens

- [x] Register / Login forms (reactive)
- [x] MFA verify
- [x] Dashboard cards balances
- [x] Accounts list
- [x] Transfer: select from, toAccountNumber, amount, description; send Idempotency-Key (uuid each submit)
- [x] History table paginated
- [x] Profile view/edit + link MFA setup

## NgRx

- [x] login/logout/refresh effects
- [x] loadAccounts effect
- [x] createTransfer effect → reload accounts + history

## Done when

E2E manual: login → open account (if none) → transfer → see history → notification backend log ✅ (Phase 6)
