# Communication Patterns

## 1. Client → Gateway → Service (sync)

```
Browser → Gateway (JWT validate, rate limit) → Service
```

- Frontend **chỉ** biết `http://localhost:8080`
- Services không expose public (demo vẫn map port để debug)

## 2. Service → Service (sync OpenFeign)

| Caller | Callee | API (internal) | Khi nào |
|--------|--------|----------------|---------|
| transaction | account | `POST /internal/accounts/{id}/reserve` | Saga step debit source |
| transaction | account | `POST /internal/accounts/{id}/credit` | Saga step credit dest |
| transaction | account | `POST /internal/accounts/{id}/release` | Compensate / release hold |
| transaction | account | `GET /internal/accounts/{id}` | Validate ownership/status |
| customer | auth | (optional) none MVP | — |
| account | customer | `GET /internal/customers/{id}/exists` | Validate customer khi mở TK |

### Internal API rules

- Prefix: `/internal/**`
- Gateway **không route** `/internal/**` ra ngoài (block)
- Header bắt buộc: `X-Internal-Api-Key` (shared secret env) **hoặc** mTLS (MVP: API key)
- Idempotency: mọi mutate internal nhận `commandId` UUID

## 3. Service → Kafka (async)

```
transaction-service  --Outbox poller-->  Kafka  -->  notification-service
```

- Producer **không** publish trực tiếp trong business TX nếu có thể tránh: dùng **Outbox table** cùng TX với saga state
- Consumer: at-least-once → **idempotent** bằng `event_id` unique table

## 4. Resilience

Trên Feign client tới account-service:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      accountService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  retry:
    instances:
      accountService:
        maxAttempts: 3
        waitDuration: 200ms
        retryExceptions: [feign.FeignException.InternalServerError, java.io.IOException]
```

- Timeout Feign: connect 2s, read 5s
- Khi CB open: saga mark FAILED + user message “service tạm thời không khả dụng”

## 5. Correlation

Mỗi request gateway sinh hoặc forward:

```
X-Correlation-Id: uuid
```

Propagate Feign + Kafka headers (`correlationId` field in event payload).

## 6. Không dùng

- gRPC (MVP)
- Message request-reply Kafka cho transfer (dùng Feign sync trong saga)
- Shared DB queries
