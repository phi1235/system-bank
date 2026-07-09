# Information Architecture — Product-grade (không demo-sơ)

> Mục tiêu: layout **mở rộng được** theo roadmap thật (banking + ops), không khóa UX vào “chỉ chuyển tiền + 3 màn admin”.

---

## 1. Customer Portal — Internet Banking

### Shell (bắt buộc)

```
┌──────────────────────────────────────────────────────────┐
│  TOP BAR: Logo | Primary Nav | Search | Noti | User menu │
├──────────────────────────────────────────────────────────┤
│  (optional) Sub-nav / breadcrumb / promo strip             │
├──────────────────────────────────────────────────────────┤
│                                                            │
│                     PAGE CONTENT                           │
│                                                            │
├──────────────────────────────────────────────────────────┤
│  FOOTER: links · bảo mật · hotline · copyright · app store │
└──────────────────────────────────────────────────────────┘
```

- **Không** dùng left sidebar cho customer (tránh giống admin / SaaS nội bộ).
- Header sticky; footer luôn có trên mọi màn (trừ full-screen MFA/login có thể rút gọn footer).

### Primary navigation (header) — nhóm chức năng

| Nav item | Route prefix | MVP | Phase sau |
|----------|--------------|-----|-----------|
| **Tổng quan** | `/customer/home` | ✅ Dashboard | widgets, insight |
| **Tài khoản** | `/customer/accounts` | ✅ list/detail | sổ phụ, sao kê PDF |
| **Chuyển tiền** | `/customer/payments` | ✅ transfer nội bộ | 247, batch, lịch |
| **Thẻ** | `/customer/cards` | 🔲 placeholder | phát hành, khóa thẻ, hạn mức |
| **Tiết kiệm / Đầu tư** | `/customer/wealth` | 🔲 placeholder | mở sổ, tất toán |
| **Khoản vay** | `/customer/loans` | 🔲 placeholder | lịch trả, đáo hạn |
| **Ưu đãi** | `/customer/offers` | 🔲 placeholder | voucher |
| **Hỗ trợ** | `/customer/support` | 🔲 placeholder | ticket, FAQ, chat |
| **Hồ sơ** (user menu) | `/customer/profile` | ✅ + MFA | eKYC, thiết bị tin cậy |

User menu (avatar dropdown): Hồ sơ · Bảo mật/MFA · Thiết bị · Đăng xuất.

### Footer blocks

- Sản phẩm · Bảo mật & quyền riêng tư · Điều khoản · Biểu phí  
- Hotline 24/7 · Chi nhánh · App iOS/Android (placeholder)  
- © Bank System · Phiên bản / môi trường (dev badge)

### Design principles (customer)

1. **Task-first:** CTA “Chuyển tiền” nổi trên header hoặc home.  
2. **Scalable IA:** thêm item nav không vỡ layout (dropdown “Thêm” khi > 6 items).  
3. **Trust:** badge bảo mật, mask số TK, confirm trước submit.  
4. **Responsive:** desktop header → mobile bottom tab (Home · Accounts · Pay · More).

---

## 2. Admin Portal — Back Office + RBAC

### Shell

```
┌──────┬───────────────────────────────────────────────────┐
│ LOGO │  Module title          [env] [role] [admin user]   │
│      ├───────────────────────────────────────────────────┤
│ NAV  │  Breadcrumb · filters · primary actions             │
│ by   ├───────────────────────────────────────────────────┤
│ mod  │  Dense data grids · drawers · permission-aware UI   │
│ +    │                                                     │
│ RBAC │                                                     │
└──────┴───────────────────────────────────────────────────┘
```

Admin **được** dùng left nav (chuẩn ops console) — khác hẳn customer header/footer.

### Module map (mở rộng)

| Module | Path | MVP | Sau |
|--------|------|-----|-----|
| **Dashboard** | `/admin` | ✅ KPI ops | charts realtime |
| **Customers** | `/admin/customers` | ✅ list + KYC | risk score, segments |
| **Accounts** | `/admin/accounts` | ✅ freeze | limit, interest config |
| **Transactions** | `/admin/transactions` | ✅ monitor | reverse, investigate |
| **Cards** | `/admin/cards` | 🔲 | issue/block |
| **Notifications** | `/admin/notifications` | 🔲 | template, resend |
| **Risk & Compliance** | `/admin/risk` | 🔲 | AML rules, cases |
| **RBAC / Users** | `/admin/rbac` | ✅ mock roles | full CRUD staff |
| **Audit** | `/admin/audit` | ✅ log viewer | export, retention |
| **System** | `/admin/system` | 🔲 | feature flags, health |

### RBAC model (admin staff)

Không chỉ `ROLE_ADMIN` phẳng. Mở rộng:

| Role | Mô tả | Quyền chính |
|------|--------|-------------|
| `SUPER_ADMIN` | Toàn quyền BO | All modules + RBAC manage |
| `OPS_ADMIN` | Vận hành TK/GD | customers read, freeze, tx monitor |
| `KYC_OFFICER` | Định danh | customers KYC decide, read PII masked by policy |
| `COMPLIANCE` | Tuân thủ | audit read, risk cases, no freeze execute (optional dual control) |
| `SUPPORT` | CSKH | customer read-only, resend notify, no freeze |
| `AUDITOR` | Kiểm toán | audit + reports read-only |

Permission key format: `module:action`  
Ví dụ: `customers:read`, `customers:kyc_decide`, `accounts:freeze`, `rbac:manage`, `audit:export`.

UI rules:

- Menu item ẩn nếu không có bất kỳ permission module.  
- Nút Freeze disabled + tooltip nếu thiếu `accounts:freeze`.  
- Trang `/admin/rbac` chỉ `SUPER_ADMIN` / `rbac:manage`.

### JWT / auth note (backend alignment)

MVP hiện tại: `roles: ["ADMIN"]`.  
Roadmap: `roles` + `permissions[]` claim **hoặc** auth-service load permissions theo role (DB `roles`, `permissions`, `role_permissions`, `staff_users`).

Xem `docs/05-frontend/RBAC.md`.

---

## 3. Shared vs không shared

| Shared | Không shared |
|--------|----------------|
| Design tokens base, typography | Customer header/footer vs Admin sidenav |
| Auth token storage, HTTP client | Visual identity (teal IB vs amber BO) |
| Money pipe, date pipe | IA / primary navigation |
| Error toast patterns | RBAC matrix (admin only) |

---

## 4. Angular folder target (cập nhật)

```
features/
  customer/
    layout/          # header + footer shell
    home/
    accounts/
    payments/
    cards/           # placeholder route + empty state
    wealth/          # placeholder
    support/         # placeholder
    profile/
  admin/
    layout/          # sidenav by module + permission
    dashboard/
    customers/
    accounts/
    transactions/
    rbac/
    audit/
    cards/           # placeholder
    risk/            # placeholder
```
