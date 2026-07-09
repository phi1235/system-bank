# Hai portal — product grade

> Chi tiết IA: `INFORMATION_ARCHITECTURE.md` · RBAC: `RBAC.md`

## Portal A — Internet Banking (Customer)

| | |
|--|--|
| Realm | `INTERNET_BANKING` |
| Role | `CUSTOMER` |
| Shell | **Header + subbar + Footer** (không left sidebar) |
| Login | `/auth/login` (+ register) |
| App | `/customer/*` |
| Visual | Navy / teal, consumer banking |
| Nav | Tổng quan · Tài khoản · Chuyển tiền · Thẻ · Thêm (Wealth, Support…) |
| Footer | Sản phẩm · Hỗ trợ · Pháp lý · hotline |

Mở rộng: cards, wealth, loans, offers, support — **placeholder route** ngay từ đầu.

## Portal B — Back Office (Admin)

| | |
|--|--|
| Realm | `BACK_OFFICE` |
| Roles | SUPER_ADMIN, OPS_ADMIN, KYC_OFFICER, COMPLIANCE, SUPPORT, AUDITOR |
| Shell | **Left module nav** + dense topbar (ops console) |
| Login | `/admin/login` (không register public) |
| App | `/admin/*` |
| Visual | Slate / amber |
| Modules | Dashboard, Customers, Accounts, Tx, Risk, RBAC, Audit (+ Cards soon) |

Permission keys: `module:action` — FE ẩn menu/nút, BE `@PreAuthorize` bắt buộc.

## Cấm

- Customer layout sidebar giống admin  
- Admin dùng header/footer “ngân hàng bán lẻ”  
- Một menu trộn freeze + chuyển tiền cá nhân  
- Hard-code mọi staff = full admin (phải RBAC)

## Mockup

`frontend/ui-mockups/` · gallery 2 cột · Ctrl+F5 sau khi pull.
