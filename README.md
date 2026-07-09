# System Bank

Microservices platform for **internet banking** (customer) and **back-office operations** (admin).

Built as a portfolio-grade distributed system: service isolation, transactional transfer saga, event-driven notifications, JWT/MFA security, and dual Angular portals.

---

## Architecture overview

```
Browser (Angular) ──► API Gateway (JWT · CORS · rate limit)
                            │
        ┌───────────────────┼───────────────────┐
        ▼         ▼         ▼         ▼         ▼
      Auth    Customer   Account  Transaction  (admin routes)
        │         │         ▲         │
        │         │         │ Feign   │ Outbox → Kafka
        │         │         └─────────┘              │
        │         │                                  ▼
        └─────────┴────────── Eureka ◄────── Notification
                    │
         PostgreSQL (DB per service) · Redis · Zipkin · Prometheus
```

| Service | Responsibility |
|---------|----------------|
| **api-gateway** | Edge routing, JWT validation, rate limiting |
| **discovery-server** | Netflix Eureka service registry |
| **auth-service** | Register/login, JWT, refresh, MFA TOTP, admin seed |
| **customer-service** | Profile, KYC status, encrypted PII |
| **account-service** | Accounts, balances, ledger, freeze/unfreeze |
| **transaction-service** | Internal transfer, saga orchestration, outbox, audit |
| **notification-service** | Kafka consumers, mock email/SMS delivery log |

**Data isolation:** logical database-per-service (`bank_auth`, `bank_customer`, `bank_account`, `bank_transaction`, `bank_notification`) on one Postgres instance.

---

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.3, Spring Cloud 2023.0 |
| Communication | OpenFeign, Resilience4j, Apache Kafka |
| Data | PostgreSQL 16, Flyway, Redis 7 |
| Security | JWT (HS256), BCrypt + pepper-bound password, AES-GCM (PII/MFA) |
| Frontend | Angular 19, NgRx, Angular Material, ngx-translate (vi/en) |
| Ops | Docker Compose, Eureka, Zipkin, Prometheus, Grafana |
| CI | GitHub Actions (`mvn verify`, Angular build) |

---

## Features

### Customer (Internet Banking)
- Register / login / optional MFA (TOTP)
- Profile management
- Open accounts, view balances
- Internal transfer with **Idempotency-Key**
- Transfer history

### Admin (Back Office)
- Staff login (bootstrap admin from env)
- Customer list & KYC update
- Account freeze / unfreeze
- Transaction monitor & audit log

### Platform patterns
- **Saga** transfer: debit → credit; credit failure → compensate (refund)
- **Transactional outbox** → Kafka → notification consumer (idempotent)
- Gateway **rate limit** (login + global) via Redis
- Distributed **tracing** (Micrometer → Zipkin) and **metrics** (Prometheus)

---

## Prerequisites

- Docker & Docker Compose v2  
- Optional (local builds only): JDK 21, Maven 3.9+, Node.js 20+  
- Recommended **≥ 16 GB RAM** for full `docker compose --build`

---

## Quick start

### 1. Clone and configure environment

```bash
git clone https://github.com/phi1235/system-bank.git
cd system-bank

cp infra/.env.example infra/.env
```

Edit `infra/.env` and replace every `change-me-*` value. Generate secrets:

```bash
openssl rand -base64 32   # AES_SECRET_KEY (32 bytes, base64)
openssl rand -hex 32      # PASSWORD_PEPPER / INTERNAL_API_KEY
openssl rand -base64 48   # JWT_SECRET (≥ 32 chars after decode or use as-is long string)
```

| File | Purpose | Version control |
|------|---------|-----------------|
| `infra/.env.example` | Template | Committed |
| `infra/.env` | Real secrets | **Never commit** (gitignored) |

See [docs/06-infra/ENV_AND_SECRETS.md](docs/06-infra/ENV_AND_SECRETS.md) for the full variable catalog.

### 2. Start infrastructure and services

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

On first Postgres boot (empty volume), databases are created automatically via  
`infra/postgres/init-databases.sql`.  
Schemas are applied by **Flyway** when each service starts.

If databases are missing on an existing volume:

```bash
./infra/scripts/init-databases.sh
```

### 3. Run the frontend

```bash
cd frontend/bank-angular-app
npm install
npm start
```

| Portal | URL |
|--------|-----|
| Customer (Internet Banking) | http://localhost:4200/auth/login |
| Admin (Back Office) | http://localhost:4200/admin/login |

### 4. Verify

```bash
curl -s http://localhost:8761/actuator/health
curl -s http://localhost:8080/actuator/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/customers/me   # expect 401
```

---

## Service endpoints

| Component | URL |
|-----------|-----|
| Eureka Dashboard | http://localhost:8761 |
| API Gateway | http://localhost:8080 |
| Auth (direct) | http://localhost:18081 |
| Customer | http://localhost:18082 |
| Account | http://localhost:18083 |
| Transaction | http://localhost:18084 |
| Notification | http://localhost:18085 |
| Zipkin | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| PostgreSQL (host) | `localhost:5433` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` |

Gateway public API base path: `/api/v1/...`

---

## Default accounts

Credentials come from `infra/.env` (not hardcoded in application code).

| Role | How to obtain | Portal |
|------|----------------|--------|
| **Admin** | Seeded on first start from `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `/admin/login` |
| **Customer** | Self-register via UI or `POST /api/v1/auth/register` | `/auth/login` |

Set `ADMIN_SEED_ENABLED=false` after bootstrap if you no longer want auto-seed.

Internal debug APIs require header:

```http
X-Internal-Api-Key: <INTERNAL_API_KEY from .env>
```

---

## Common operations

### Transfer happy path (UI)

1. Register and log in as customer  
2. Create profile (if prompted)  
3. Open two accounts  
4. Transfer from A → B using destination **account number**  
5. Check history and balances  

### Notifications

```bash
curl -s http://localhost:18085/internal/notifications \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY"

docker logs bank-notification 2>&1 | grep MOCK_EMAIL | tail
```

### Saga compensation demo

```bash
SAGA_FAIL_CREDIT=true docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
# perform a transfer → status COMPENSATED, source balance restored

SAGA_FAIL_CREDIT=false docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
```

### Login rate limit

```bash
for i in $(seq 1 8); do
  curl -s -o /dev/null -w "$i %{http_code}\n" \
    -X POST http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"nope","password":"bad"}'
done
# expect HTTP 429 after repeated failures
```

### Local compile only (no Docker images)

```bash
cd backend && mvn -T 1C compile -DskipTests
cd frontend/bank-angular-app && npm run lint
```

---

## Branching

| Branch | Environment | Purpose |
|--------|-------------|---------|
| **`main`** | **STG** | Staging / stable integration |
| **`uat/v1.0.0`** | **UAT** | Release line for v1.0.0 |

**Workflow:** `feat/*` or `fix/*` → MR into `uat/v1.0.0` → (when ready) MR into `main` (STG).

Details: [docs/07-devops/BRANCHING.md](docs/07-devops/BRANCHING.md)

```bash
git fetch origin
git checkout uat/v1.0.0 && git pull
git checkout -b feat/your-feature
# ... work, push, open MR → uat/v1.0.0
```

---

## Project structure

```
system-bank/
├── backend/                 # Maven multi-module Spring services
│   ├── common-lib/
│   ├── discovery-server/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── customer-service/
│   ├── account-service/
│   ├── transaction-service/
│   └── notification-service/
├── frontend/
│   ├── bank-angular-app/    # Angular 19 (customer + admin portals)
│   └── ui-mockups/          # Static design reference
├── infra/
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── postgres/
│   ├── prometheus/
│   ├── grafana/
│   └── scripts/init-databases.sh
├── docs/                    # Architecture, API, security, ADRs
└── .github/workflows/ci.yml
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/01-architecture/architecture.md) | System design |
| [Saga transfer](docs/01-architecture/saga-transfer.md) | Transfer orchestration |
| [Security](docs/01-architecture/security.md) | AuthN/Z, crypto, rate limit |
| [Environment & secrets](docs/06-infra/ENV_AND_SECRETS.md) | Env variable reference |
| [API contracts](docs/03-api/contracts/) | Service APIs |
| [Demo script](docs/DEMO_SCRIPT.md) | End-to-end walkthrough |
| [Known limitations](docs/KNOWN_LIMITATIONS.md) | Scope boundaries |
| [ADRs](docs/99-decisions/) | Architecture decisions |

---

## Security notes

- Passwords are stored as **one-way hashes** (HMAC with server pepper + username, then BCrypt)—never plaintext.  
- Secrets load only from environment variables; YAML does not embed production defaults for secrets.  
- MFA TOTP secrets and national ID are encrypted at rest (AES-GCM).  
- Do not commit `infra/.env`. Rotate keys if they were ever exposed.

---

## License

This project is provided for educational and portfolio demonstration purposes.
Use and modify at your own risk; it is **not** a production core-banking system.
