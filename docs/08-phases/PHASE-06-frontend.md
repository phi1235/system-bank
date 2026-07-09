# PHASE 06 — Angular FE

## Read

- `docs/05-frontend/**`

## Checklist

- [x] Scaffold + core interceptors (auth / refresh / error)
- [x] Customer portal MVP screens (header/footer shell)
- [x] Admin portal MVP (left nav shell)
- [x] NgRx auth / accounts / transfers
- [x] Lazy routes (`auth`, `customer`, `admin`)
- [x] Env `apiUrl` + `proxy.conf.json` → gateway `:8080`
- [x] Placeholder routes (cards, wealth, support, risk)
- [x] Admin freeze account UI
- [x] MFA setup/enable on profile + MFA verify login

## Status

**DONE** (2026-07-09)

### Path

`frontend/bank-angular-app/` — Angular 19 standalone + Material + NgRx 19

### Run

```bash
cd frontend/bank-angular-app && npm start
# Customer: http://localhost:4200/auth/login
# Admin:    http://localhost:4200/admin/login  (admin / Admin123!)
```

### Happy path (manual)

1. Register + login customer  
2. Profile create (if needed)  
3. Open 2 accounts  
4. Transfer A→B → history shows COMPLETED  
5. Admin login → freeze fromAccountId  
6. Customer transfer again → ACCOUNT_FROZEN  

### Build

`ng build` development OK (lazy chunks per feature)

## Exit

UI happy path + admin freeze blocks transfer. ✅

Next: **Phase 7** — Ops / security / CI polish (`PHASE-07-ops-security.md`)
