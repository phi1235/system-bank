# PROVIDE LATER — Hardcode / mock / key cần bạn cung cấp

> **Mục đích:** Ghi nhận mọi chỗ hiện đang dùng **giá trị dev/mock/hardcode**.  
> Khi muốn chạy “thật” (staging/prod hoặc provider thật), **điền cột “Giá trị bạn cung cấp”** rồi bảo AI gắn vào code/env.

**Cách dùng**

1. Copy section **Form điền nhanh** ở cuối → paste chat / PR comment.
2. Hoặc sửa trực tiếp file này (cột `YOUR_VALUE`).
3. AI chỉ swap theo list này — không tự invent secret.

**Trạng thái cột**

| Status | Nghĩa |
|--------|--------|
| `DEV_OK` | Chạy demo local ổn; đổi khi deploy thật |
| `MOCK` | Logic giả (log only); cần provider thật |
| `HARDCODE` | Nằm trong code (text/const); có thể extract config |
| `NEED_YOU` | Bắt buộc bạn quyết định/cung cấp mới “production-like” |

---

## 1. Secrets & credentials (env)

Nguồn chính: `infra/.env` (+ default trong `application.yml` / `docker-compose.yml`).  
**Không commit secret thật.** Chỉ điền vào `.env` local hoặc secret store.

| ID | Biến / key | Default dev hiện tại | Dùng ở | Status | YOUR_VALUE |
|----|------------|----------------------|--------|--------|------------|
| S1 | `POSTGRES_USER` | `bank` | Postgres + services | DEV_OK | |
| S2 | `POSTGRES_PASSWORD` | `bank` | Postgres + services | DEV_OK | |
| S3 | `JWT_SECRET` | `bank-system-dev-jwt-secret-key-min-32-chars!!` | auth-service, api-gateway | DEV_OK → **đổi khi deploy** (≥32 bytes) | |
| S4 | `AES_SECRET_KEY` | Base64 mock `MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=` (= `0123456789abcdef0123456789abcdef`) | auth (MFA secret), customer (PII/nationalId) | DEV_OK → **đổi** (base64 32-byte key) | |
| S5 | `INTERNAL_API_KEY` | `dev-internal-api-key-change-me` | account / customer / transaction / notification internal APIs | DEV_OK → **đổi** | |
| S6 | `ADMIN_USERNAME` | `admin` | auth seed admin | DEV_OK | |
| S7 | `ADMIN_PASSWORD` | `Admin123!` | auth seed admin | DEV_OK → **đổi** | |
| S7b | `ADMIN_SEED_ENABLED` | `true` | tắt seed sau bootstrap | DEV_OK → `false` prod | |
| S7c | `PASSWORD_PEPPER` | dev default string | HMAC bind password+username before BCrypt | DEV_OK → **random dài** | |
| S8 | `ADMIN_EMAIL` | `admin@bank.local` | auth seed | DEV_OK | |
| S9 | `CORS_ORIGINS` | `http://localhost:4200,http://localhost:5173` | api-gateway | DEV_OK → URL FE prod | |
| S10 | `JWT_ACCESS_TTL` | `900` (15m) | auth | DEV_OK | |
| S11 | `JWT_REFRESH_TTL` | `604800` (7d) | auth | DEV_OK | |
| S12 | `JWT_MFA_TTL` | `300` (5m) | auth | DEV_OK | |

**File template:** `infra/.env.example`  
**File runtime (local):** `infra/.env`

---

## 2. Notification — mock → real provider

| ID | Hiện tại | File / chỗ | Cần bạn cung cấp | Status | YOUR_VALUE |
|----|----------|------------|------------------|--------|------------|
| N1 | `MockEmailSender` chỉ `log.info("MOCK_EMAIL ...")` | `backend/notification-service/.../MockEmailSender.java` | Provider: SMTP / SES / SendGrid / Resend… + API key / SMTP host/user/pass | MOCK | Provider: ___ |
| N2 | `MockSmsSender` chỉ `log.info("MOCK_SMS ...")` | `.../MockSmsSender.java` | Provider: Twilio / Viettel / brandname SMS + credentials | MOCK | Provider: ___ |
| N3 | Recipient email fallback: `user-{userId}@bank.local` | `NotificationHandler.java` | Domain email thật? Lấy email từ customer-service event? | HARDCODE | Domain / strategy: ___ |
| N4 | SMS always `+84000000000` | `NotificationHandler.java` | Map SĐT từ customer profile (`phone`) | HARDCODE | |
| N5 | Subject/body tiếng Anh hardcode (`Transfer completed`, `Transfer failed/compensated`, template body string) | `NotificationHandler.buildBody` | Copy email/SMS brand (VI/EN), template ID | HARDCODE | |
| N6 | Template codes: `TRANSFER_COMPLETED`, `TRANSFER_FAILED` | `NotificationHandler` | Có map sang template ID provider không? | DEV_OK | |
| N7 | Event chưa luôn có `recipientEmail` (transaction payload) | `TransferSagaOrchestrator.baseEvent` | Muốn enrich email/phone vào Kafka event? (cần customer lookup) | NEED_YOU | yes/no + rule |

**Khi bạn cung cấp N1/N2:** AI sẽ:

- Thêm env `EMAIL_*` / `SMS_*`
- Implement adapter thật + giữ mock profile `local`
- (Optional) wire phone/email từ customer-service

---

## 3. Branding / copy / product text

| ID | Hiện tại | Chỗ | Status | YOUR_VALUE |
|----|----------|-----|--------|------------|
| B1 | MFA issuer `"BankSystem"` | `MfaService.java` (otpauth / QR) | HARDCODE | Tên app Authenticator: ___ |
| B2 | JWT realm `"INTERNET_BANKING"` | `JwtService.java` | HARDCODE | Giữ / đổi: ___ |
| B3 | Product name docs/README: **Bank System** | README, mockups, docs | DEV_OK | Tên thương hiệu UI: ___ |
| B4 | Footer mockup: `© Bank System` | `docs/05-frontend/INFORMATION_ARCHITECTURE.md` + ui-mockups | HARDCODE (FE) | |
| B5 | Design tokens navy/teal | `frontend/ui-mockups/css/tokens.css` | DEV_OK | Brand color hex (nếu khác): ___ |
| B6 | Logo / favicon | Chưa có asset thật | NEED_YOU | Path/URL logo SVG/PNG |
| B7 | Support phone / hotline / email footer | Placeholder mockups | NEED_YOU | |

---

## 4. Business rules demo (có thể giữ cho portfolio)

| ID | Hiện tại | Chỗ | Status | YOUR_VALUE / quyết định |
|----|----------|-----|--------|-------------------------|
| R1 | Số dư mở TK mặc định **1_000_000 VND** | `bank.account.initial-balance`, ADR-008 | HARDCODE demo | Số thật / 0 + nạp riêng: ___ |
| R2 | Currency cố định **VND** | account open, transfer default | HARDCODE | Multi-currency? (out of MVP) |
| R3 | Max **3** accounts / user | `bank.account.max-per-user` | DEV_OK | |
| R4 | Account type default `PAYMENT` | `AccountController` / entity | DEV_OK | Tên product type: ___ |
| R5 | Account number random 10 digits | `AccountAppService.generateAccountNumber` | DEV_OK | Format số TK bank thật? |
| R6 | `SAGA_FAIL_CREDIT` inject fail demo | env + orchestrator message `"Injected credit failure for demo"` | DEV_OK (chỉ demo) | Tắt prod: `false` |
| R7 | Login lock: 5 fails / 15 phút | `bank.security.login-*` | DEV_OK | |
| R8 | Password policy (độ mạnh) | auth validation | DEV_OK | Rule bank nội bộ? |
| R9 | KYC status mock `PENDING` | customer create | MOCK | Flow KYC thật? (out MVP) |

---

## 5. Infra / host mapping (dev)

| ID | Hiện tại | Status | YOUR_VALUE (prod) |
|----|----------|--------|-------------------|
| I1 | Postgres host map `5433→5432` | DEV_OK | Managed PG URL |
| I2 | Redis single node no auth | DEV_OK | Redis URL + password |
| I3 | Kafka single broker `kafka:9092` | DEV_OK | MSK / Confluent bootstrap |
| I4 | Eureka + single compose | DEV_OK | K8s / cloud discovery (later) |
| I5 | Zipkin local `:9411` | DEV_OK | Cloud APM (optional) |
| I6 | Topics: `bank.transaction.completed` / `failed` | DEV_OK | Prefix env? |
| I7 | DB names: `bank_auth`, `bank_customer`, `bank_account`, `bank_transaction`, `bank_notification` | DEV_OK | |

---

## 6. Frontend (Phase 6 — đã scaffold)

Path: `frontend/bank-angular-app/` · env: `src/environments/environment*.ts`

| ID | Cần | Status | YOUR_VALUE |
|----|-----|--------|------------|
| F1 | `apiUrl` gateway base | DEV_OK | default `http://localhost:8080/api/v1` |
| F2 | OAuth social login? | Out of scope MVP | skip / provider |
| F3 | reCAPTCHA / Turnstile register? | Chưa có | keys nếu muốn |
| F4 | Analytics (GA / Mixpanel) | Chưa có | |
| F5 | i18n default locale | HARDCODE UI tiếng Việt | `vi` / `en` |
| F6 | App store / deep link | Placeholder footer | |
| F7 | Brand strings in FE shell | HARDCODE `Bank System` | |
| F8 | Material theme palettes | DEV_OK | brand hex |

---

## 7. Provider credentials — template điền (khi sẵn sàng)

```yaml
# === EMAIL (chọn 1) ===
# EMAIL_PROVIDER: smtp | ses | sendgrid | resend | mock
EMAIL_PROVIDER: mock
SMTP_HOST:
SMTP_PORT: 587
SMTP_USER:
SMTP_PASSWORD:
SMTP_FROM: noreply@your-bank.example
# SENDGRID_API_KEY:
# RESEND_API_KEY:
# AWS_SES_REGION:
# AWS_ACCESS_KEY_ID:
# AWS_SECRET_ACCESS_KEY:

# === SMS (chọn 1) ===
# SMS_PROVIDER: twilio | mock | other
SMS_PROVIDER: mock
SMS_FROM:   # brandname or number
TWILIO_ACCOUNT_SID:
TWILIO_AUTH_TOKEN:
# OTHER_SMS_API_URL:
# OTHER_SMS_API_KEY:

# === BRAND ===
BRAND_NAME: Bank System
MFA_ISSUER: BankSystem
NOTIFICATION_EMAIL_DOMAIN: bank.local   # fallback only
DEFAULT_CURRENCY: VND
DEMO_INITIAL_BALANCE: 1000000

# === SECRETS (generate mới, không reuse dev) ===
JWT_SECRET:
AES_SECRET_KEY:   # base64(32 bytes)
INTERNAL_API_KEY:
ADMIN_PASSWORD:
POSTGRES_PASSWORD:
```

---

## 8. Checklist “gắn vào code” (AI làm sau khi bạn fill)

Khi bạn cung cấp xong, nhờ AI theo thứ tự:

- [ ] Cập nhật `infra/.env.example` + document keys mới
- [ ] Wire compose env → services
- [ ] Thay `MockEmailSender` / `MockSmsSender` bằng adapter + profile `local|real`
- [ ] Enrich transfer event: email + phone từ customer (nếu N7=yes)
- [ ] Externalize template subject/body (N5) hoặc i18n
- [ ] Đổi MFA issuer + brand strings (B1–B4)
- [ ] (Optional) initial-balance = 0 + seed script
- [ ] Rotate JWT/AES/INTERNAL/admin passwords; rebuild stack
- [ ] Smoke E2E: transfer → email/SMS thật / log provider

---

## 9. Form điền nhanh (copy/paste cho AI)

```text
=== PROVIDE_LATER fill ===
JWT_SECRET:
AES_SECRET_KEY:   # base64 32 bytes
INTERNAL_API_KEY:
ADMIN_USERNAME:
ADMIN_PASSWORD:
ADMIN_EMAIL:
POSTGRES_PASSWORD:
CORS_ORIGINS:

EMAIL_PROVIDER: mock|smtp|sendgrid|ses|resend
# điền field provider tương ứng:
SMTP_HOST / USER / PASS / FROM:
SENDGRID_API_KEY:
...

SMS_PROVIDER: mock|twilio|other
SMS_FROM:
TWILIO_ACCOUNT_SID / AUTH_TOKEN:

BRAND_NAME:
MFA_ISSUER:
LOGO_PATH_OR_URL:
HOTLINE:
SUPPORT_EMAIL:

NOTIFICATION: enrich_recipient_from_customer=yes|no
DEFAULT_CURRENCY: VND
DEMO_INITIAL_BALANCE: 1000000 | 0
DEFAULT_LOCALE: vi|en

Notes:
```

---

## 10. Tham chiếu code nhanh

| Chủ đề | Path |
|--------|------|
| Env sample | `infra/.env.example` |
| Compose inject | `infra/docker-compose.yml` |
| JWT/AES/admin | `backend/auth-service/src/main/resources/application.yml` |
| Gateway JWT/CORS | `backend/api-gateway/src/main/resources/application.yml` |
| Initial balance | `backend/account-service/.../application.yml` + `AccountAppService` |
| Saga fail inject | `backend/transaction-service/.../TransferSagaOrchestrator.java` |
| Mock notify | `backend/notification-service/.../MockEmailSender.java`, `MockSmsSender.java`, `NotificationHandler.java` |
| MFA issuer | `backend/auth-service/.../MfaService.java` |
| PII AES | `backend/customer-service/.../CustomerAppService.java` |
| Security design | `docs/01-architecture/security.md` |
| Demo balance ADR | `docs/99-decisions/ADR-008-demo-initial-balance.md` |

---

*Last updated: 2026-07-09 (sau Phase 5). Cập nhật file này mỗi khi thêm hardcode/mock mới.*
