# IMPLEMENT — admin-portal features

## Checklist

- [x] Admin login (same auth, ROLE_ADMIN)
- [x] Customers table + KYC patch
- [x] Lookup account / list transfers
- [x] Freeze / unfreeze account buttons
- [x] Audit log table (simple)
- [x] RBAC matrix mock page

## Guard

Only `ADMIN` role; CUSTOMER hitting /admin → redirect customer home. ✅
