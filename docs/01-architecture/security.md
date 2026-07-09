# Security Design

## 1. Authentication

### Register
- `POST /api/v1/auth/register` { username/email, password, fullName }
- Password: BCrypt strength 10+
- Create auth user ROLE_CUSTOMER
- Emit logical link: customer-service tạo profile skeleton (sync Feign **hoặc** event — MVP: **auth gọi Feign customer internal create** sau register thành công; nếu fail → compensating delete user **hoặc** lazy create on first login)

**MVP chốt:** Register chỉ tạo auth user; **first login / dedicated endpoint** `POST /customers/me` init profile. Đơn giản hơn (ADR-006).

### Login
1. Validate credentials
2. Nếu MFA enabled → return `{ mfaRequired: true, mfaToken }` (short-lived JWT 5m, scope MFA_PENDING)
3. Client gửi `POST /auth/mfa/verify` { mfaToken, code }
4. Issue access + refresh

### Tokens
| Token | TTL | Store |
|-------|-----|-------|
| Access JWT | 15m | client memory |
| Refresh | 7d | Redis + HttpOnly cookie **hoặc** body (SPA: body + memory; document XSS) |
| MFA pending | 5m | client |

JWT claims:
```json
{
  "sub": "userId",
  "username": "...",
  "roles": ["CUSTOMER"],
  "jti": "uuid",
  "typ": "access"
}
```

Algorithm: HS256 MVP với `JWT_SECRET` ≥ 256-bit (env).  
(Upgrade RS256 later — không bắt buộc.)

### Refresh
- `POST /auth/refresh` { refreshToken }
- Rotate refresh: blacklist old jti Redis
- Return new access + refresh

### Logout
- Blacklist access jti + delete refresh Redis

## 2. MFA TOTP

- Library: `dev.samstevens.totp` hoặc similar
- `POST /auth/mfa/setup` → QR otpauth URI + temp secret Redis
- `POST /auth/mfa/enable` { code } → persist secret encrypted
- `POST /auth/mfa/disable` { code, password } — optional MVP
- Window: ±1 step

Secret at rest: AES encrypt (cùng key infra PII) hoặc encrypt riêng `MFA_SECRET_KEY`.

## 3. Authorization RBAC

| Role | Quyền |
|------|-------|
| CUSTOMER | own profile, own accounts, own transfers |
| ADMIN | list customers, freeze account, view any transaction (read) |

Gateway hoặc service method security:
- `@PreAuthorize("hasRole('ADMIN')")` cho admin endpoints
- Resource ownership check trong service (account.userId == principal)

## 4. Sensitive data

| Data | Protection |
|------|------------|
| password | **Bound hash**: HMAC-SHA256(pepper, password‖username) → BCrypt(12). Pepper = env `PASSWORD_PEPPER` (not in DB). Prevents hash-paste across users / raw BCrypt inject. |
| TOTP secret | AES-GCM |
| nationalId (CMND/CCCD) | AES-GCM, never log plaintext |
| account number | mask in logs (****1234) |

AES key: `AES_SECRET_KEY` env (base64 32 bytes).  
Helper trong `common-lib`: `CryptoUtils.encrypt/decrypt`.

## 5. Gateway

- CORS: FE origin only
- Rate limit: Redis RequestRateLimiter **hoặc** Bucket4j  
  - Global: 100 req/min / IP  
  - `/auth/login`: 5 / min / IP
- Strip sensitive headers to backends if needed
- Validate JWT signature; forward `X-User-Id`, `X-User-Roles` **chỉ sau khi verify** (services tin headers **chỉ** từ gateway network — defense: internal API key vẫn cần cho service-service)

**Note:** Services cũng parse JWT **hoặc** trust gateway headers trong Docker network.  
**MVP chốt:** Gateway validate + pass headers; services accept headers when request from docker network + optional internal key for `/internal`.

## 6. Brute force

Redis counter `auth:login:fail:{ip}`:
- ≥ 5 fails / 15m → 429 + lock message
- Reset on success

## 7. Audit log

Bảng `audit_log` trong **transaction-service** (và optional auth):

| Field | Example |
|-------|---------|
| actorUserId | uuid |
| action | TRANSFER_CREATE, LOGIN_SUCCESS, ACCOUNT_FREEZE |
| resourceType | TRANSFER |
| resourceId | uuid |
| ip | |
| metadata JSON | |
| createdAt | |

Admin đọc audit qua API đơn giản.

## 8. OWASP checklist MVP

- [x] No default passwords in prod docs
- [x] SQL injection: JPA parameterized
- [x] XSS: FE Angular default sanitization
- [x] CSRF: JWT SPA (no cookie session) → low risk; if cookie refresh → SameSite
- [x] Mass assignment: DTO separate
- [x] Secure headers: gateway Spring Security headers basic

## 9. Secrets never commit

`.env.example` only. Real `.env` gitignored.
