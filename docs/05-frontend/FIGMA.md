# UI Design Spec — Bank System

## Trạng thái Figma cloud

| Item | Status |
|------|--------|
| PAT token (`figd_…`) | ✅ Valid (REST API `/v1/me`) |
| Grok MCP tools hiện có | `get_figma_data`, `download_figma_images` (**read-only**) |
| `use_figma` / `create_new_file` / `generate_figma_design` | ❌ Không có trong session |
| Design deliverable | ✅ **HTML mockups** tại `frontend/ui-mockups/` |

> **Không commit PAT vào git.** Token đã lộ trong chat → **nên revoke & tạo token mới** trên Figma → Settings → Security.

### Để AI vẽ trực tiếp lên Figma sau này

Cần **Figma official remote MCP** (OAuth), không chỉ PAT:

```bash
# Grok — HTTP MCP chính thức
grok mcp add --transport http figma-official https://mcp.figma.com/mcp
# Sau đó login OAuth theo hướng dẫn Figma (Dev Mode MCP / browser auth)
```

Hoặc bật **Figma Desktop → Dev Mode → Enable desktop MCP server** rồi trỏ Grok vào local MCP.

Khi có write tools, prompt:

```
Tạo file Figma "Bank System UI" từ frontend/ui-mockups/
Tokens: css/tokens.css
Screens: map trong docs/05-frontend/FIGMA.md
Desktop 1440, mỗi màn 1 frame
```

---

## Xem mockup ngay

```bash
cd bank-system/frontend/ui-mockups
python3 -m http.server 5173
# mở http://localhost:5173
```

Hoặc mở trực tiếp `index.html` trong browser.

---

## Design tokens (→ Angular Material theme / Figma variables)

| Token | Value | Use |
|-------|-------|-----|
| primary-900 | `#0a1628` | Sidebar, dark brand |
| primary-700 | `#143a5c` | Primary button |
| accent | `#00b4a6` | CTA, active nav |
| bg | `#f4f6f9` | Page background |
| surface | `#ffffff` | Cards |
| success / danger | `#0d9f6e` / `#dc2626` | Status money |
| radius-lg | `16px` | Cards |
| font | DM Sans | UI text |
| mono | JetBrains Mono | Account numbers, IDs |

File: `frontend/ui-mockups/css/tokens.css`

---

## Hai portal — screen map → Angular routes

Chi tiết ranh giới: **`PORTALS.md`**.

### Portal A — Internet Banking (header + footer)

| Hash | Route | Ghi chú |
|------|-------|---------|
| `#login` `#register` `#mfa` | `/auth/*` | Auth IB |
| `#home` | `/customer/home` | Dashboard + quick actions |
| `#accounts` | `/customer/accounts` | |
| `#transfer` | `/customer/payments/transfer` | Module Payments |
| `#history` | `/customer/payments/history` | |
| `#profile` | `/customer/profile` | |
| `#cards` `#wealth` `#support` | placeholder routes | Nav/footer sẵn |

Shell: **header nav ngang + CTA + footer** (không left sidebar).

### Portal B — Back Office (modules + RBAC)

| Hash | Route | Ghi chú |
|------|-------|---------|
| `#admin-login` | `/admin/login` | Staff only |
| `#admin-overview` | `/admin` | KPI + queues |
| `#admin-customers` | `/admin/customers` | KYC |
| `#admin-accounts` | `/admin/accounts` | Freeze + permission demo |
| `#admin-transfers` | `/admin/transactions` | Monitor |
| `#admin-rbac` | `/admin/rbac` | Matrix roles × permissions |
| `#admin-audit` | `/admin/audit` | |
| `#admin-risk` | `/admin/risk` | Placeholder |

Shell: **left module nav** · slate/amber · permission-aware.

---

## Components checklist (Angular Material mapping)

| UI element | Material / custom |
|------------|-------------------|
| Buttons primary/outline/danger | `mat-flat-button`, `mat-stroked-button` |
| Text fields | `mat-form-field` outline |
| OTP 6 boxes | custom component |
| Data table | `mat-table` + paginator |
| Badges status | custom chip / `mat-chip` |
| Sidebar nav | `mat-nav-list` in sidenav |
| Cards / stats | `mat-card` |
| Dialogs confirm transfer | `MatDialog` |

---

## Import HTML → Figma (thủ công, không MCP write)

1. Mở mockup fullscreen 1440×900  
2. Screenshot từng `#screen-*`  
3. Figma → Place image → frame 1440  
4. (Optional) plugin **html.to.design** / **Anima** paste URL `localhost:5173/#login`  

---

## AI coding prompt (Phase 6)

```
Implement Angular FE bank-system Phase 6.
Visual source of truth: frontend/ui-mockups/ (open index.html)
Tokens: frontend/ui-mockups/css/tokens.css
Screens map: docs/05-frontend/FIGMA.md
Architecture: docs/05-frontend/ARCHITECTURE.md
Match layout/spacing/colors; use Angular Material per ADR-009.
```
