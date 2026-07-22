# System Bank

Microservices platform for **internet banking** (customer) and **back-office operations** (admin).

Distributed banking demo: service isolation, transfer saga, event-driven notifications, JWT/MFA security, and dual Angular portals.

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
> **Note:** rontend/ui-mockups/ is a **static design prototype** (hard-coded sample copy/data for UX review). The production UI is rontend/bank-angular-app/ with API + i18n.

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
- Gateway strips caller identity headers, then signs downstream identity with **HMAC-SHA256**
- Backend services and actuator endpoints are isolated on the internal Docker network

---

## Prerequisites

- Docker & Docker Compose v2  
- Optional (local builds only): JDK 21, Maven 3.9+, Node.js 20+  
- Recommended **≥ 16 GB RAM** for full `docker compose --build`

---

## Branching

| Branch | Purpose |
|--------|---------|
| **`main`** | Protected, deployable integration baseline |
| **`feat/*`** | Feature work created from current `main` |
| **`fix/*`** | Bug and security fixes created from current `main` |
| **`uat/*`** | Optional release-candidate line cut from `main` |

Current workflow: branch from updated `main`, open a PR back to `main`, pass CI/security review, then merge. Cut `uat/*` only when a release candidate needs dedicated validation.

```bash
git fetch origin
git switch main && git pull --ff-only
git switch -c fix/short-description   # or feat/short-description
# work -> test -> push -> open PR targeting main
```

---

## Quick start

### 1. Clone and configure environment

```bash
git clone https://github.com/phi1235/system-bank.git
cd system-bank

cp infra/.env.example infra/.env
```

`infra/.env.example` is a **names-only checklist** (empty values + comments). Copy it to `infra/.env` and fill **every** variable for your environment. Never put real values back into the tracked template.

```bash
openssl rand -base64 32   # AES_SECRET_KEY
openssl rand -hex 32      # Generate pepper/signing/internal keys separately
openssl rand -base64 48   # JWT_SECRET (use a long random string)
openssl rand -base64 16 | tr -d '='   # KAFKA_CLUSTER_ID

git check-ignore infra/.env            # must print infra/.env
```

| File | Purpose | Git |
|------|---------|-----|
| `infra/.env.example` | Variable names + setup comments only (no real data) | Committed |
| `infra/.env` | All environment-specific values and secrets | **Ignored; never commit** |
| `application.yml` | Environment references; no secret or port fallback | Committed |
| `docker-compose.yml` | Environment references; no literal port mapping | Committed |

Required secrets include: `POSTGRES_PASSWORD`, `JWT_SECRET`, `AES_SECRET_KEY`, `GATEWAY_SIGNING_SECRET`, each service-specific `*_INTERNAL_API_KEY`, `PASSWORD_PEPPER`, `ADMIN_PASSWORD`, and `GRAFANA_ADMIN_PASSWORD`. Also set ports, hosts, topics, and other non-secret keys listed in the template — Compose/YAML have no baked-in defaults for them.
Migration note: remove the former shared `INTERNAL_API_KEY`; use the three service-specific keys shown in `.env.example`.

Docker Compose fails before startup when a required secret or `KAFKA_CLUSTER_ID` is empty.

### 2. Start infrastructure and services

**Full stack** (build/run all Java service images):

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env config --quiet
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

**Faster day-to-day loop** — infra only, run Spring Boot on the host/IDE:

```bash
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d
# then: mvn -pl auth-service -am spring-boot:run  (from backend/)
```

**Rebuild a single service image** (avoid full-stack rebuild):

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env build auth-service
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d auth-service
```

Dockerfiles under `backend/*/Dockerfile` use:
- `backend/.dockerignore` (skip `target/`, IDE noise)
- POM-first layers + copy only that service’s sources (plus `common-lib` when needed)
- BuildKit Maven cache (`RUN --mount=type=cache,target=/root/.m2`)

Requires **Docker BuildKit** (Compose v2 / modern Docker Desktop — on by default).

On first Postgres boot (empty volume), DBs are created via `infra/postgres/init-databases.sql`.  
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
docker compose -f infra/docker-compose.yml --env-file infra/.env exec api-gateway sh -c \
  'wget -qO- "http://localhost:${MANAGEMENT_SERVER_PORT}/actuator/health"'
API_PORT=$(awk -F= '$1=="API_GATEWAY_HOST_PORT" {print $2}' infra/.env)
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:${API_PORT}/api/v1/customers/me"  # expect 401
```

---

## Ports and service exposure

No Docker port is defined as a literal in Compose. Change ports only in `infra/.env`:

| Component | Host port variable | Exposure |
|-----------|--------------------|----------|
| API Gateway | `API_GATEWAY_HOST_PORT` | Published on `HOST_BIND_ADDRESS` |
| PostgreSQL | `POSTGRES_HOST_PORT` | Loopback development access |
| Redis | `REDIS_HOST_PORT` | Loopback development access |
| Kafka | `KAFKA_HOST_PORT` | Loopback development access |
| Zipkin | `ZIPKIN_HOST_PORT` | Loopback development access |
| Prometheus | `PROMETHEUS_HOST_PORT` | Loopback development access |
| Grafana | `GRAFANA_HOST_PORT` | Loopback development access |

Auth, customer, account, transaction, notification, Eureka, internal APIs and actuator ports are not published to the host. They are reachable only inside `bank-net`; external clients must use the API Gateway.

Gateway public API base path: `/api/v1/...`

---

## Default accounts

Credentials come from `infra/.env` (not hardcoded in application source).

| Role | How to obtain | Portal |
|------|----------------|--------|
| **Admin** | Seeded on first start from `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `/admin/login` |
| **Customer** | Self-register via UI or `POST /api/v1/auth/register` | `/auth/login` |

Set `ADMIN_SEED_ENABLED=false` after bootstrap if you no longer want auto-seed.

Internal debug APIs require:

```http
X-Internal-Api-Key: <the target service's *_INTERNAL_API_KEY from .env>
```

---

## Common operations

### Transfer happy path (UI)

1. Register and log in as customer  
2. Create profile (if prompted)  
3. Open two accounts  
4. Transfer A → B using destination **account number**  
5. Check history and balances  

### Notifications

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env exec notification-service sh -c \
  'wget -qO- --header="X-Internal-Api-Key: $NOTIFICATION_INTERNAL_API_KEY" \
  "http://localhost:${SERVER_PORT}/internal/notifications"'

docker logs bank-notification 2>&1 | grep MOCK_EMAIL | tail
```

### Saga compensation demo

```bash
SAGA_FAIL_CREDIT=true docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
# transfer → COMPENSATED, source balance restored

SAGA_FAIL_CREDIT=false docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
```

### Login rate limit

```bash
API_PORT=$(awk -F= '$1=="API_GATEWAY_HOST_PORT" {print $2}' infra/.env)
LOGIN_LIMIT=$(awk -F= '$1=="RATE_LIMIT_LOGIN" {print $2}' infra/.env)
ATTEMPTS=$((LOGIN_LIMIT + 3))
for i in $(seq 1 "$ATTEMPTS"); do
  curl -s -o /dev/null -w "$i %{http_code}\n" \
    -X POST "http://localhost:${API_PORT}/api/v1/auth/login" \
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
│   ├── bank-angular-app/    # Angular 19 (customer + admin)
│   └── ui-mockups/          # Static design reference
├── infra/
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── postgres/
│   ├── prometheus/
│   ├── grafana/
│   └── scripts/init-databases.sh
└── .github/workflows/ci.yml
```

---

## Security notes

- Passwords are stored as **one-way hashes** (HMAC with server pepper + username, then BCrypt)—never plaintext.  
- Secrets load only from environment variables; YAML does not embed secret production defaults.  
- Tracked `infra/.env.example` keeps **all values blank** (names + comments only); real data belongs only in ignored `infra/.env` or a production secret manager.  
- `application.yml` and Docker Compose contain environment references instead of literal service ports or secret defaults.  
- Reserved identity/signature headers are stripped from external requests; downstream identity is signed with HMAC.  
- Direct backend and actuator ports are not published to the host.  
- Private keys, keystores, secret directories and local application-secret files are ignored by Git.  
- MFA TOTP secrets and national ID are encrypted at rest (AES-GCM).  
- Do not commit `infra/.env`. Rotate keys if they were ever exposed.

---

## License

Educational and portfolio demonstration purposes only.  
**Not** a production core-banking system.
