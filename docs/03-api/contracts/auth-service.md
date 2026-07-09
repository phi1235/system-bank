# Contract — auth-service

Base (via GW): `/api/v1/auth`

## POST /register

Request:
```json
{
  "username": "nguyenphi",
  "email": "phi@example.com",
  "password": "Secret123!",
  "fullName": "Nguyen Phi"
}
```

Response 201:
```json
{ "userId": "uuid", "username": "nguyenphi" }
```

Errors: `USERNAME_TAKEN`, `EMAIL_TAKEN`, `WEAK_PASSWORD`

## POST /login

Request: `{ "username": "...", "password": "..." }`

Response A (no MFA):
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "mfaRequired": false
}
```

Response B (MFA):
```json
{
  "mfaRequired": true,
  "mfaToken": "short-jwt"
}
```

## POST /mfa/verify

`{ "mfaToken": "...", "code": "123456" }` → tokens như login A

## POST /mfa/setup  (JWT)

→ `{ "otpauthUri": "otpauth://...", "secret": "BASE32_FOR_DEV_ONLY" }`  
(Production-like: secret only once; QR data)

## POST /mfa/enable  (JWT)

`{ "code": "123456" }` → `{ "mfaEnabled": true }`

## POST /refresh

`{ "refreshToken": "..." }` → new tokens

## POST /logout  (JWT)

Blacklist + revoke refresh

## GET /me  (JWT)

```json
{
  "userId": "uuid",
  "username": "...",
  "email": "...",
  "roles": ["CUSTOMER"],
  "mfaEnabled": true
}
```

## Internal

None critical for MVP (customer uses same userId).
