# Architecture — Tổng quan hệ thống

## Context diagram

```
┌─────────────┐     HTTPS      ┌──────────────────┐
│  Angular FE │ ─────────────► │   API Gateway    │
│  (browser)  │                │  JWT · RateLimit │
└─────────────┘                └────────┬─────────┘
                                        │ lb / routes
           ┌────────────────────────────┼────────────────────────────┐
           ▼              ▼             ▼             ▼              ▼
     auth-service  customer-svc  account-svc  transaction-svc  (admin routes)
           │              │             │             │
           │              │             │             ├── Feign ──► account-svc
           │              │             │             └── produce Kafka
           └──────────────┴─────────────┴───────────────┐
                                                        ▼
                                              notification-svc
                                              (Kafka consumer)
           All services ◄── register ── Eureka (discovery-server)
           Infra: PostgreSQL×N · Redis · Kafka · Zipkin · Prometheus
```

## Style

- **Modular monolith? Không.** True multi-process microservices (Docker).
- **Sync:** REST + OpenFeign (request/response, query, reserve/debit)
- **Async:** Kafka domain events (notification, optional audit stream)
- **Consistency:** Saga orchestration cho transfer (không 2PC)

## Service responsibilities (ranh giới)

| Service | Owns | Không được |
|---------|------|------------|
| auth | credentials, tokens, MFA secrets, roles | profile chi tiết, balance |
| customer | PII profile, KYC status | password, account balance |
| account | accounts, balances, status | transfer orchestration |
| transaction | transfer order, saga state, ledger entries (local), outbox | mutate balance trực tiếp DB account |
| notification | delivery log, templates | business decision |
| gateway | edge concerns only | business logic |
| discovery | registry only | — |

## Logical identity

- `userId` (UUID) — auth primary, reference ở customer
- `customerId` (UUID) — customer primary (= userId 1:1 MVP cho đơn giản **hoặc** map table)
- `accountId` (UUID) + `accountNumber` (string 10–14 digits)
- `transactionId` (UUID)

**MVP quyết định (ADR-004):** `customerId == userId` (1 user = 1 customer). Giảm join logic.

## Deployment units

Mỗi service:

```
Dockerfile → jar → container
env: DB_URL, KAFKA, EUREKA, JWT_*, REDIS
```

Compose network: `bank-net`

## Non-functional (MVP targets)

| NFR | Target |
|-----|--------|
| Availability | Single node demo |
| Latency transfer | < 2s happy path local |
| Security | JWT + MFA + RBAC + rate limit login |
| Traceability | Zipkin span gateway → services |
| Idempotency | Transfer by `Idempotency-Key` header |

## Package layout chuẩn mỗi service

```
com.banksystem.<svc>/
  config/
  domain/          # entity, enum, repository
  application/     # service, saga, usecase
  api/             # controller, dto, mapper
  infrastructure/  # feign, kafka, redis, outbox
  security/        # nếu cần local
```

## Đọc tiếp

- Communication: `communication.md`
- Saga: `saga-transfer.md`
- Security: `security.md`
- Service map ports: `service-map.md`
