# Frontend Architecture — Angular

## Path

`frontend/bank-angular-app/`

## Stack

- Angular 17+ standalone **or** NgModule (chốt: **standalone + lazy routes**)
- NgRx Store + Effects (auth, accounts, transfers)
- Angular Material **hoặc** Tailwind — **chốt Material** (nhanh form/table) ADR-009
- RxJS
- Api base: `environment.apiUrl = http://localhost:8080/api/v1`

## Hai portal tách biệt (bắt buộc)

Xem chi tiết: **`docs/05-frontend/PORTALS.md`**.

| Portal | Base path | Role | Shell |
|--------|-----------|------|-------|
| Internet Banking | `/auth/*`, `/customer/*` | CUSTOMER | Consumer banking (navy/teal) |
| Back Office | `/admin/login`, `/admin/*` | ADMIN | Ops console (slate/amber) |

**Cấm:** chung sidebar, chung “mode switch”, admin thấy menu chuyển khoản, customer vào freeze KYC.

## App structure

```
src/app/
  core/                 # interceptors, guards, auth API (shared logic only)
  shared/               # pipes, table primitives (không phải layout portal)
  features/
    customer-auth/      # login / register / mfa — brand IB
    customer/           # dashboard, accounts, transfer, history, profile
    admin-auth/         # admin login only — brand BO
    admin/              # overview, customers, accounts, transfers, audit
  layouts/
    customer-shell/     # sidenav IB only
    admin-shell/        # sidenav BO only
  store/
```

## Lazy loading

```ts
// Public customer auth
{ path: 'auth', loadChildren: () => import('./features/customer-auth/routes') }

// Customer app — ONLY ROLE_CUSTOMER (ADMIN redirect to /admin)
{
  path: 'customer',
  canActivate: [authGuard, roleGuard(['CUSTOMER'])],
  loadChildren: () => import('./features/customer/routes')
}

// Admin auth + app — ONLY ROLE_ADMIN
{ path: 'admin/login', loadComponent: () => import('...AdminLogin') }
{
  path: 'admin',
  canActivate: [authGuard, roleGuard(['ADMIN'])],
  loadChildren: () => import('./features/admin/routes')
}
```

## Core

| Piece | Behavior |
|-------|----------|
| AuthInterceptor | attach Bearer accessToken |
| RefreshInterceptor | on 401 queue requests → refresh → retry 1 |
| ErrorInterceptor | toast map error.code |
| authGuard | require token |
| roleGuard | check roles claim /me |

## NgRx slices (không over-store)

- `auth`: user, tokens, mfaPending, status
- `accounts`: list, selected
- `transfers`: create form status, history page

Local component state for pure UI.

## Screens

### Customer (Internet Banking — header/footer)

**MVP:** Login/Register/MFA · Home · Accounts · Transfer · History · Profile(+MFA)  
**Placeholder routes (nav sẵn):** Cards · Wealth · Support  

Shell: sticky header + primary nav + CTA + footer sản phẩm. **Không** left sidebar.

### Admin (Back Office — module nav + RBAC)

**MVP:** Login · Dashboard · Customers/KYC · Accounts freeze · Tx monitor · RBAC matrix · Audit  
**Placeholder:** Risk/Compliance · Cards admin  

Shell: left module nav; nút/menu theo `permissions[]`. Xem `RBAC.md`.

## UX product-grade

- Hai realm / hai visual identity  
- Customer scalable IA (dropdown “Thêm”)  
- Admin permission-aware UI + audit  
- Loading, confirm transfer, mask PII  
- Responsive: IB desktop header → mobile bottom tabs (phase sau)

## Out of scope (vẫn ghi roadmap, không code hết MVP)

- i18n full, PWA, micro-frontends  
- Full AML engine, card issuing production  
→ Placeholder UI + docs IA bắt buộc để không vỡ kiến trúc khi expand
