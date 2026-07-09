# IMPLEMENT — auth-service

## Goal

Register, login, JWT access/refresh, logout blacklist, MFA TOTP, roles.

## Path

`backend/auth-service/`

## Read first

- `docs/03-api/contracts/auth-service.md`
- `docs/02-data/er-diagrams/auth-service.md`
- `docs/01-architecture/security.md`
- `docs/02-data/redis-usage.md`

## Stack

- Spring Web, Data JPA, Security, Validation
- Flyway, PostgreSQL
- Redis
- Eureka client, Actuator, springdoc
- TOTP library
- common-lib

## Package layout

```
api/ AuthController
application/ AuthService, TokenService, MfaService
domain/ User, MfaSettings, repos
infrastructure/ redis, jwt
config/ SecurityConfig
```

## Checklist

- [ ] Flyway V1 schema
- [ ] Register + BCrypt
- [ ] Login + fail counter Redis
- [ ] JWT access + refresh rotate
- [ ] Logout blacklist
- [ ] MFA setup/enable/verify
- [ ] GET /me
- [ ] Seed admin user (compose env or data.sql) username `admin` / password from env
- [ ] Swagger
- [ ] Unit tests: password, token, mfa code window
- [ ] Dockerfile

## Security notes

- Never log password/secret
- MFA secret AES encrypted at rest

## Done when

Postman collection: register → login → setup MFA → enable → login MFA → refresh → logout works.
