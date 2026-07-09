# Admin RBAC — Back Office

## Mục tiêu

Phân quyền **nhân viên nội bộ** (không nhầm với ROLE_CUSTOMER trên Internet Banking).  
Hỗ trợ dự án mở rộng: nhiều team ops/kyc/compliance, không hard-code “mọi admin full quyền”.

## Domain model (auth-service hoặc admin-iam module sau)

```
staff_users ──< staff_user_roles >── roles ──< role_permissions >── permissions
```

| Entity | Fields (core) |
|--------|----------------|
| staff_users | id, username, email, password_hash, enabled, mfa_enabled |
| roles | id, code (SUPER_ADMIN…), name, description |
| permissions | id, code (`accounts:freeze`), module, description |
| role_permissions | role_id, permission_id |
| staff_user_roles | user_id, role_id |

Customer `users` (Internet Banking) **tách bảng** hoặc tách realm — không gán permission BO cho customer.

## Permission catalog (MVP + seed)

| Code | Module | Mô tả |
|------|--------|--------|
| `dashboard:read` | dashboard | Xem KPI |
| `customers:read` | customers | List/detail KH |
| `customers:kyc_decide` | customers | Verify/Reject KYC |
| `accounts:read` | accounts | List TK hệ thống |
| `accounts:freeze` | accounts | Freeze/Unfreeze |
| `transactions:read` | transactions | Monitor GD |
| `transactions:investigate` | transactions | Xem saga steps / detail |
| `audit:read` | audit | Xem audit log |
| `audit:export` | audit | Export (sau) |
| `rbac:read` | rbac | Xem roles |
| `rbac:manage` | rbac | Sửa gán role |
| `notifications:resend` | notifications | Gửi lại (sau) |
| `risk:read` | risk | Case AML (sau) |

## Role → permissions (seed)

| Role | Permissions |
|------|-------------|
| SUPER_ADMIN | `*` |
| OPS_ADMIN | dashboard:read, customers:read, accounts:*, transactions:*, audit:read |
| KYC_OFFICER | dashboard:read, customers:read, customers:kyc_decide, audit:read |
| COMPLIANCE | dashboard:read, customers:read, transactions:read, audit:*, risk:read |
| SUPPORT | dashboard:read, customers:read, notifications:resend |
| AUDITOR | dashboard:read, audit:read, transactions:read, customers:read |

## FE enforcement

1. `AuthState.permissions: string[]` sau login admin.  
2. `*hasPermission="'accounts:freeze'"` directive.  
3. `permissionGuard('rbac:manage')` trên route.  
4. Menu config khai báo `requiredPermission` — filter lúc render.

## BE enforcement

- Mọi admin API: `@PreAuthorize("hasAuthority('accounts:freeze')")` hoặc equivalent.  
- FE ẩn nút **không** thay thế BE check.

## JWT claim (đề xuất)

```json
{
  "sub": "staff-uuid",
  "typ": "access",
  "realm": "BACK_OFFICE",
  "roles": ["OPS_ADMIN"],
  "permissions": ["accounts:freeze", "customers:read", "..."]
}
```

Customer token: `"realm": "INTERNET_BANKING"`, `roles: ["CUSTOMER"]`, không có permissions BO.

## UI màn RBAC (mock)

- Roles table  
- Permission matrix (role × permission checkboxes) — SUPER_ADMIN only  
- Assign roles to staff user  
- “You lack permission” empty state khi SUPPORT mở /admin/accounts freeze

## Phase gắn

| Phase | Việc |
|-------|------|
| P2 auth | roles ADMIN phẳng vẫn chạy |
| P6 FE | mock permissions + directive + menu filter |
| P7+ | full tables + seed + PreAuthorize theo permission |

MVP demo có thể seed 1 SUPER_ADMIN + 1 SUPPORT để show ẩn/hiện nút Freeze.
