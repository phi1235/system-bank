# Service Map — Ports, routes, DB

## Ports (host)

| Component | Container port | Host port | Notes |
|-----------|----------------|-----------|-------|
| api-gateway | 8080 | 8080 | Public entry |
| discovery-server | 8761 | 8761 | Eureka UI |
| auth-service | 8081 | 8081 | Internal + via GW |
| customer-service | 8082 | 8082 | |
| account-service | 8083 | 8083 | |
| transaction-service | 8084 | 8084 | |
| notification-service | 8085 | 8085 | |
| postgres-auth | 5432 | 5433 | map tránh conflict local |
| postgres-customer | 5432 | 5434 | |
| postgres-account | 5432 | 5435 | |
| postgres-transaction | 5432 | 5436 | |
| postgres-notification | 5432 | 5437 | |
| redis | 6379 | 6379 | |
| kafka | 9092 | 9092 | |
| zookeeper / kraft | — | theo compose | Ưu tiên KRaft 1 broker |
| zipkin | 9411 | 9411 | |
| prometheus | 9090 | 9090 | |
| grafana | 3000 | 3000 | optional dashboard |

## Gateway routes

| Path prefix | Target service | Auth |
|-------------|----------------|------|
| `/api/v1/auth/**` | auth-service | Public (trừ `/me`) |
| `/api/v1/customers/**` | customer-service | JWT |
| `/api/v1/accounts/**` | account-service | JWT |
| `/api/v1/transactions/**` | transaction-service | JWT |
| `/api/v1/admin/**` | multi / same services | JWT + ROLE_ADMIN |
| `/actuator/health` | each (optional aggregate) | Internal |

Strip prefix: **không strip** `/api/v1` — service expose cùng path (dễ debug).

## Eureka service IDs

```
AUTH-SERVICE
CUSTOMER-SERVICE
ACCOUNT-SERVICE
TRANSACTION-SERVICE
NOTIFICATION-SERVICE
```

Gateway dùng `lb://AUTH-SERVICE` etc.

## Databases

| DB name | Owner |
|---------|-------|
| `bank_auth` | auth-service |
| `bank_customer` | customer-service |
| `bank_account` | account-service |
| `bank_transaction` | transaction-service |
| `bank_notification` | notification-service |

User/password: env `POSTGRES_USER` / `POSTGRES_PASSWORD` (compose defaults dev-only).

## Kafka topics

| Topic | Producer | Consumer | Key |
|-------|----------|----------|-----|
| `bank.transaction.completed` | transaction-service | notification-service | transactionId |
| `bank.transaction.failed` | transaction-service | notification-service | transactionId |
| `bank.audit.events` (optional) | any | (log only / future) | userId |

Retention: 7 days dev.

## Redis keys

| Key pattern | TTL | Purpose |
|-------------|-----|---------|
| `auth:refresh:{jti}` | refresh TTL | refresh token store |
| `auth:blacklist:{jti}` | remaining access TTL | logout / revoke |
| `auth:login:fail:{ip}` | 15m | brute-force counter |
| `mfa:setup:{userId}` | 10m | pending TOTP secret |

## Health

Mỗi service: `GET /actuator/health`  
Compose: `healthcheck` + `depends_on: condition: service_healthy` cho DB/Kafka.
