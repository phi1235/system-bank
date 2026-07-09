# Security & dynamic design — hiện trạng + hướng chuẩn

> Trả lời: database thế nào? admin hard ở đâu? vì sao không “set cứng” mọi thứ? password/DB/XSS/DDoS?

---

## 1. Database hiện tại (đã có)

### Logical DB-per-service (ADR-007)

Một Postgres container, **5 database**:

| DB | Service |
|----|---------|
| `bank_auth` | users, MFA, auth audit |
| `bank_customer` | profile, KYC, PII encrypted |
| `bank_account` | accounts, ledger |
| `bank_transaction` | transfer orders, outbox, audit |
| `bank_notification` | notification_logs, processed_events |

- Schema mỗi service = Flyway `V1__init.sql` riêng  
- **Không** FK chéo DB; liên kết bằng UUID `userId`  
- Init: `infra/postgres/init-databases.sql`

### Mật khẩu trong DB (sau harden)

- Cột `users.password_hash` = **BCrypt** (one-way), **không** lưu plaintext, **không** AES decrypt được password.  
- Material hash bind: `HMAC-SHA256(PASSWORD_PEPPER, password || username)` → rồi BCrypt.  
- `PASSWORD_PEPPER` chỉ có trên **server env**, không nằm trong DB.

→ Hacker dump DB:

| Tấn công | Kết quả |
|----------|---------|
| Đọc hash | Không ra được plaintext (BCrypt + pepper) |
| Copy hash user A sang user B | **Login B fail** (hash gắn username) |
| Tự insert BCrypt thô của `"123456"` | **Fail** (thiếu HMAC pepper + username) |
| Biết pepper + offline brute | Vẫn khó (BCrypt cost 12); pepper làm rainbow table vô dụng |

**Lưu ý industry:** “Mã hóa 2 chiều password để giải mã check username” là **anti-pattern** (nếu lộ key → lộ hết pass). Chuẩn = **hash 1 chiều + salt (trong BCrypt) + pepper (server) + bind identity**.

---

## 2. Admin có hardcode trong code không?

**Không hard password trong source production path.**

| Thứ | Nơi | Ý nghĩa |
|-----|-----|---------|
| `AdminSeedRunner` | bootstrap **nếu** DB trống | Tiện demo |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | **env** (`infra/.env`) | Secret deploy |
| `ADMIN_SEED_ENABLED=false` | env | Tắt seed sau khi có staff thật |
| Default `admin`/`Admin123!` | `.env.example` only | Dev convenience — **đổi khi share** |

Register public → luôn `CUSTOMER`. ADMIN không tự đăng ký public (portal tách).

**Hướng động hơn (roadmap):** bỏ seed; API invite staff + RBAC table (roles/permissions) trong `bank_auth`.

---

## 3. “Phát triển động” vs set cứng — nguyên tắc

| Cứng (tránh) | Động (ưu tiên) |
|--------------|----------------|
| `if (role == "ADMIN")` rải rác business | Permission keys `accounts:freeze` load từ DB/JWT |
| Feature flag hardcode true | Config / env / remote flag |
| Template email hardcode EN | Template table / i18n |
| 1 role string | `roles` + `role_permissions` |

**MVP hiện tại (honest):**  
- Roles string `CUSTOMER` / `ADMIN` trên user (đủ demo 2 portal).  
- FE có **RBAC matrix mock** (docs) — backend chưa full permission engine.

**Thiết kế RBAC động (implement khi có phase security):**

```
users ──< user_roles >── roles ──< role_permissions >── permissions
permission: module:action  e.g. accounts:freeze, customers:kyc_decide
```

JWT claim: `permissions[]` hoặc load mỗi request từ cache Redis.

---

## 4. Bảo mật transport password (client → server)

| Lớp | MVP hiện tại | Nâng cao (portfolio+) |
|-----|--------------|------------------------|
| TLS/HTTPS | Compose local HTTP; prod bắt buộc TLS terminate | Gateway TLS 1.2+ |
| Body password | JSON plaintext **trên TLS** | Optional: RSA-OAEP encrypt password field bằng public key server (`GET /auth/crypto/public-key`) |
| XSS steal token | Angular sanitize; token sessionStorage (risk documented) | HttpOnly cookie refresh + CSP strict |
| CSRF | JWT SPA low risk | SameSite nếu cookie |

**TLS đã mã hóa cả request** (kể cả password) trên đường truyền. Encrypt thêm field password = defense-in-depth khi log proxy / mitm TLS broken — không thay TLS.

---

## 5. XSS / injection / DDoS — map hiện tại

| Threat | Control đã có | Còn thiếu |
|--------|---------------|-----------|
| XSS | Angular default escaping | CSP header FE host; sanitize rich text |
| SQLi | JPA parameterized | — |
| Brute force login | Redis fail counter + gateway rate limit 5/min | CAPTCHA optional |
| DDoS / flood | Gateway global rate 100/min/IP | WAF / cloud shield ngoài app |
| Mass assignment | DTO records | — |
| Internal path leak | Gateway block `/internal/**` | mTLS later |
| PII dump | AES-GCM nationalId | Field-level audit |
| Token theft | Short access TTL, refresh rotate, logout blacklist | Device binding |

---

## 6. Design pattern ưu tiên trong repo

| Pattern | Chỗ |
|---------|-----|
| API Gateway | edge authz/rate limit |
| DB-per-service | isolation |
| Saga orchestration | transfer |
| Transactional Outbox | reliable events |
| Idempotent consumer | notification |
| Feign + Circuit breaker | account calls |
| Bound password hash | auth (pepper + username) |
| Seed bootstrap (env) | first admin only |

---

## 7. Roadmap implement (không phình một lần)

| Priority | Item | Effort |
|----------|------|--------|
| P0 ✅ | Bound password (pepper + username) + seed env flag | Done |
| P1 | FE HTTPS local + document CSP | Small |
| P1 | Optional RSA password envelope register/login | Medium |
| P2 | RBAC tables + permission checks | Large |
| P2 | Staff invite API (no public admin register) | Medium |
| P3 | Argon2id thay BCrypt | Small |
| P3 | WAF / cloud DDoS | Ops |

Secrets list: `docs/00-overview/PROVIDE_LATER.md` (`PASSWORD_PEPPER`, `ADMIN_*`).

---

## 8. Trả lời nhanh interviewer

**“Password encrypt trong DB?”**  
→ Không encrypt 2 chiều; **hash** BCrypt + **pepper** server + **bind username**.

**“Admin hardcode?”**  
→ Bootstrap từ env; tắt `ADMIN_SEED_ENABLED`; production tạo staff qua quy trình.

**“Động role?”**  
→ MVP 2 role demo; design permission matrix documented; implement P2.

**“Client mã hóa pass?”**  
→ TLS bắt buộc; optional field encryption public-key cho wow + defense-in-depth.
