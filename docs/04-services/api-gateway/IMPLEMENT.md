# IMPLEMENT — api-gateway

## Goal

Single entry: routing, CORS, JWT validation, rate limit login, correlation id.

## Path

`backend/api-gateway/`

## Stack

- spring-cloud-starter-gateway
- eureka client
- redis (rate limiter optional)
- jjwt or spring-security-oauth2-jose for JWT

## Routes (see service-map)

```yaml
spring.cloud.gateway.routes:
  - id: auth
    uri: lb://AUTH-SERVICE
    predicates: [Path=/api/v1/auth/**]
  - id: customers
    uri: lb://CUSTOMER-SERVICE
    predicates: [Path=/api/v1/customers/**]
  # accounts, transactions similarly
```

## Filters custom

1. **CorrelationIdFilter** — generate/forward `X-Correlation-Id`
2. **JwtAuthFilter** — skip public paths; validate JWT; set `X-User-Id`, `X-User-Roles`
3. **BlockInternalFilter** — reject `/internal/**` from outside

Public paths:
- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/refresh`
- `/api/v1/auth/mfa/verify`
- actuator health

## Rate limiting

- Login path: 5/min/IP (Redis RequestRateLimiter or Bucket4j)
- Default: 100/min/IP

## Checklist

- [ ] Gateway app + eureka
- [ ] All routes
- [ ] JWT filter + secret from env
- [ ] CORS for `http://localhost:4200`
- [ ] Rate limit login
- [ ] Dockerfile + compose
- [ ] No business logic

## Depends

- `docs/01-architecture/service-map.md`
- `docs/01-architecture/security.md`
