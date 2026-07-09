# PHASE 02 — auth-service

## Read

- `docs/04-services/auth-service/IMPLEMENT.md`
- contracts + ER auth + security + redis

## Checklist

- [x] Full auth-service per IMPLEMENT
- [x] Gateway route auth + JWT filter + Redis blacklist
- [x] Seed admin (`admin` / `Admin123!`)
- [x] Unit tests (password, JWT)
- [x] Docker compose `auth-service` (host port **18081**)

## Status

**DONE** (2026-07-09) — E2E via Gateway:
register → login → me → mfa setup/enable → login mfa → verify → refresh → logout (401 revoked) → admin login.

## Exit

Register/login/MFA/refresh/logout qua Gateway OK. ✅
