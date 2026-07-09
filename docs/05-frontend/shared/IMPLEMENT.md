# IMPLEMENT — frontend shared/core

## Checklist

- [x] Angular project scaffold (`frontend/bank-angular-app`)
- [x] environments dev/prod
- [x] core interceptors (auth, refresh, error)
- [x] guards (auth, role, guest)
- [x] ApiService base HTTP wrapper using envelope `success/data/error`
- [x] shared components: page-header, loading (confirm via `window.confirm` on transfer)
- [x] pipe: accountMask, moneyVnd
- [x] layout shells: **customer header/footer** + **admin left sidenav**

## Token storage

- accessToken: memory + sessionStorage (MVP)
- refreshToken: sessionStorage
- Clear on logout

Document XSS risk in interview notes.

## FE conventions (Phase post-8)

- [x] Component = `*.ts` + `*.html` + `*.scss` (no inline template/styles)
- [x] i18n via `@ngx-translate` (`public/i18n/vi.json`, `en.json`)
- [x] No hardcoded UI copy in templates/TS toasts
- Details: `frontend/bank-angular-app/docs/FRONTEND_CONVENTIONS.md`

