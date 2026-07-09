# Roadmap triển khai

> Ước lượng tham chiếu: part-time ~10–15h/tuần → **5–6 tuần**. Full-time: **2–3 tuần**.

```
PHASE 0  Foundation docs lock + repo skeleton
   ↓
PHASE 1  Infra + discovery + gateway skeleton + common-lib
   ↓
PHASE 2  auth-service (JWT + refresh + MFA)
   ↓
PHASE 3  customer-service + account-service
   ↓
PHASE 4  transaction-service (Saga + Outbox + Kafka produce)
   ↓
PHASE 5  notification-service + end-to-end transfer demo
   ↓
PHASE 6  Angular FE (customer + admin)
   ↓
PHASE 7  Observability + security harden + CI/CD polish
   ↓
PHASE 8  Demo script + README runbook + interview talking points
```

## Timeline gợi ý

| Phase | Focus | Done when |
|-------|-------|-----------|
| 0 | Docs + empty modules | Architecture/API docs ready |
| 1 | Compose + Eureka + Gateway + common-lib | `docker compose up` có Eureka UI |
| 2 | Auth | Login/refresh/MFA Postman pass |
| 3 | Customer + Account | Tạo user → có profile + 1 account |
| 4 | Transaction Saga | Transfer OK + fail compensate |
| 5 | Notification | Kafka event → log email |
| 6 | Frontend | Full happy path UI |
| 7 | Ops | Zipkin trace + GH Actions green |
| 8 | Demo | 15' script sẵn |

## Dependency graph (không đảo)

```
common-lib ──► mọi service
discovery-server ──► gateway + services register
auth-service ──► gateway JWT secret/public key shared
customer-service ──► auth userId (logical FK, không join DB)
account-service ──► customerId logical
transaction-service ──► account-service (Feign) + Kafka
notification-service ──► Kafka only
frontend ──► gateway only
```

## Milestone demo

- **M1 (sau P2):** Auth secured APIs  
- **M2 (sau P5):** Transfer E2E backend  
- **M3 (sau P6):** UI demo  
- **M4 (sau P8):** Interview-ready repo  

## Risk & mitigation

| Risk | Mitigation |
|------|------------|
| Saga quá phức tạp | Orchestration đơn giản 3 bước + compensate 1 bước (xem saga doc) |
| Feign + Eureka local fail | Compose network cố định, healthcheck `depends_on` |
| JWT secret lệch | 1 env `JWT_SECRET` shared gateway+auth |
| FE scope creep | Chỉ 6 màn hình customer + 3 admin |
| Scope creep | SCOPE_MVP + ADR |

## Status MVP

Phases **0–8 complete** (implementation + demo pack).  
Runtime full stack: máy đủ RAM / CI; low-RAM: `mvn compile` + docs only.

## Next action (sau MVP)

1. Demo theo `docs/DEMO_SCRIPT.md`  
2. Gắn provider thật: `docs/00-overview/PROVIDE_LATER.md`  
3. Đọc gaps: `docs/KNOWN_LIMITATIONS.md`
