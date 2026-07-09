# PHASE 03 — customer + account

## Read

- customer IMPLEMENT + account IMPLEMENT + contracts + ER

## Checklist

- [x] customer-service complete (profile AES, admin KYC, internal exists)
- [x] account-service complete (open, list, freeze, atomic debit/credit, idempotent ledger)
- [x] Gateway routes wired (existing Phase 1 routes)
- [x] E2E: login → profile → open account 1_000_000 → debit → freeze

## Status

**DONE** (2026-07-09)  
- Customer host `:18082`, Account host `:18083`  
- Debit 100k → balance 900k; idempotent same ref; insufficient 422; freeze → ACCOUNT_FROZEN  

## Exit

Account balance correct after manual internal debit test (curl internal with API key). ✅
