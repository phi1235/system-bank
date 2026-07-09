# Scope MVP — In / Out

## IN SCOPE (bắt buộc)

### Backend
- [x] api-gateway (routing, JWT, rate limit, CORS)
- [x] discovery-server (Eureka)
- [x] auth-service (register, login, refresh, logout blacklist, MFA TOTP, roles)
- [x] customer-service (CRUD profile self, KYC status mock, AES PII)
- [x] account-service (tạo TK mặc định, balance, freeze/unfreeze admin)
- [x] transaction-service (transfer nội bộ, Saga orchestrator, Outbox, audit)
- [x] notification-service (consume Kafka, mock email/SMS log)
- [x] common-lib (ApiResponse, BusinessException, security helpers)

### Patterns
- [x] Database per service (PostgreSQL)
- [x] OpenFeign sync calls
- [x] Kafka async events
- [x] Saga (orchestration) cho transfer
- [x] Transactional Outbox
- [x] Idempotency key trên transfer
- [x] Circuit breaker Resilience4j trên Feign critical calls
- [x] Redis: refresh token / JWT blacklist / rate-limit optional backend

### Frontend
- [x] Auth (login, MFA, refresh interceptor)
- [x] Customer: dashboard, accounts, transfer, history, profile
- [x] Admin: customers list, account freeze, transaction lookup
- [x] Lazy-loaded feature modules
- [x] NgRx cho auth + transfer flow

### Infra / DevOps
- [x] Single docker-compose full stack
- [x] Zipkin + Actuator + Prometheus scrape basic
- [x] GitHub Actions: build + test backend + frontend lint
- [x] OpenAPI per service

## OUT OF SCOPE (không làm trong MVP)

| Item | Lý do | Giai đoạn sau? |
|------|-------|----------------|
| card-service | Phình scope | Phase 9+ |
| config-server | Compose env đủ | Optional |
| ELK / OpenSearch | Effort cao | Optional |
| Kubernetes | Overkill portfolio 1 máy | Later |
| Multi-currency / FX | Complexity | Later |
| Real SMS/Email provider | Mock log đủ | Later |
| Mobile app | — | Later |
| GraphQL | REST đủ | No |
| CQRS full / Event sourcing full | Saga+Outbox đủ wow | Later |
| Payment gateway external | — | No |

## Scope freeze rule

Mọi feature mới phải:

1. Ghi vào issue / `docs/99-decisions/ADR-xxx.md`
2. Cập nhật SCOPE + ROADMAP
3. Mới được code

AI **không** tự thêm feature “cho hay”.
