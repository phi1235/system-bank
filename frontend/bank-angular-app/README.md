# Bank Angular App — Phase 6+

Internet Banking (customer) + Back Office (admin) portals.

## Run

```bash
# Backend stack must be up (gateway :8080)
cd frontend/bank-angular-app
npm install
npm start
# http://localhost:4200
```

| Portal | URL | Demo |
|--------|-----|------|
| Customer login | http://localhost:4200/auth/login | register new user |
| Admin login | http://localhost:4200/admin/login | `admin` / `Admin123!` |

API base: `environment.apiUrl` → `http://localhost:8080/api/v1`  
Proxy: `proxy.conf.json` (optional path `/api` → gateway)

## Features

**Customer (header/footer):** home, accounts, transfer (+ Idempotency-Key), history, profile+MFA, placeholders  

**Admin (left nav):** dashboard, customers+KYC, freeze, tx monitor, audit, RBAC mock, risk placeholder  

**NgRx:** auth / accounts / transfers  
**Guards / interceptors:** auth, role, Bearer, refresh, error toast  
**i18n:** `@ngx-translate` — `public/i18n/vi.json` + `en.json` + language switcher  
**Structure:** each component = `.ts` + `.html` + `.scss` — see `docs/FRONTEND_CONVENTIONS.md`

## Verify (low RAM)

```bash
npm run lint   # tsc --noEmit
```

## Build

```bash
npm run build
```
