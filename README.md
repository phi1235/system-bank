# Bank System — Microservices Banking Platform

Portfolio-grade internet banking + back-office: **Spring Boot / Cloud · Kafka · Angular · Docker**.

| Layer | Status |
|-------|--------|
| Design docs + UI mockups | ✅ |
| **Phase 1** — infra, discovery, gateway, common-lib | ✅ |
| **Phase 2** — auth-service (JWT, MFA, refresh) | ✅ |
| **Phase 3** — customer + account | ✅ |
| **Phase 4** — transaction Saga + Outbox | ✅ |
| **Phase 5** — notification-service (Kafka consumer) | ✅ |
| **Phase 6** — Angular FE (customer + admin) | ✅ |
| **Phase 7** — observability, CI, harden | ✅ |
| **Phase 8** — demo / interview pack | ✅ |

---

## Portfolio quick links

| Doc | Purpose |
|-----|---------|
| [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) | **15′ live demo** |
| [`docs/INTERVIEW_TALKING_POINTS.md`](docs/INTERVIEW_TALKING_POINTS.md) | Interview Q&A |
| [`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md) | Honest scope gaps |
| [`docs/01-architecture/architecture-diagram.md`](docs/01-architecture/architecture-diagram.md) | Mermaid diagrams |
| [`docs/00-overview/PROVIDE_LATER.md`](docs/00-overview/PROVIDE_LATER.md) | Real keys/providers later |

---

## Sample users

| Username | Password | Portal | Notes |
|----------|----------|--------|-------|
| `admin` | `Admin123!` | http://localhost:4200/admin/login | Seeded ADMIN (`ADMIN_*` env) |
| *(self-register)* | min strong e.g. `Test1234!` | http://localhost:4200/auth/login | ROLE_CUSTOMER |

Internal debug key (dev only): `X-Internal-Api-Key: dev-internal-api-key-change-me`

---

## Clean-machine runbook

> **RAM note:** Full `docker compose … --build` needs a machine with enough memory (≈16GB+ recommended). On 8GB: develop/compile only (`mvn compile`, `npm run lint`); run stack on a stronger PC.

### 1) Prerequisites

- Docker + Docker Compose v2  
- (Optional) JDK 21, Maven 3.9+, Node 20+

### 2) Configure secrets (required)

```bash
cp infra/.env.example infra/.env
# REQUIRED: replace every change-me-* value (JWT, AES, pepper, DB, admin, internal key)
# Generate:
#   openssl rand -base64 32   # AES_SECRET_KEY
#   openssl rand -hex 32      # PASSWORD_PEPPER / INTERNAL_API_KEY
```

| File | Git |
|------|-----|
| `infra/.env.example` | ✅ commit (template) |
| `infra/.env` | ❌ **gitignored** — real secrets |
| Root `.env.example` | ✅ pointer only |

Full catalog: **`docs/06-infra/ENV_AND_SECRETS.md`**.  
Secrets in `application.yml` / compose use `${VAR}` **without** hardcoded secret defaults.

### 3) Start full backend stack

```bash
# From repo root
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

First build can take several minutes.

**Databases:** on **first** Postgres start (empty volume), Compose auto-runs  
`infra/postgres/init-databases.sql` → creates `bank_auth`, `bank_customer`, `bank_account`, `bank_transaction`, `bank_notification`.  
Tables = **Flyway** per service on boot.

If volume already exists / need re-create DBs only:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres
./infra/scripts/init-databases.sh          # prefers bank-postgres container
# or: ./infra/scripts/init-databases.sh --host localhost --port 5433 --user bank --password bank
```

### 4) URLs

| Service | URL |
|---------|-----|
| Eureka | http://localhost:8761 |
| API Gateway | http://localhost:8080 |
| Auth (direct) | http://localhost:18081 |
| Customer | http://localhost:18082 |
| Account | http://localhost:18083 |
| Transaction | http://localhost:18084 |
| Notification | http://localhost:18085 |
| **Zipkin** | http://localhost:9411 |
| **Prometheus** | http://localhost:9090 |
| **Grafana** | http://localhost:3000 (admin/admin) |
| Postgres (host) | `localhost:5433` |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

### 5) Health checks

```bash
curl -s http://localhost:8761/actuator/health
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:18084/actuator/prometheus | head
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/customers/me   # 401
```

### 6) Frontend

```bash
cd frontend/bank-angular-app && npm install && npm start
# Customer IB:  http://localhost:4200/auth/login
# Admin BO:     http://localhost:4200/admin/login
```

### 7) Demo highlights

Follow **`docs/DEMO_SCRIPT.md`**. Short version:

1. Register/login customer → open 2 accounts → transfer  
2. Zipkin trace + `docker logs bank-notification \| grep MOCK_EMAIL`  
3. Admin freeze account → transfer blocked  
4. Optional: `SAGA_FAIL_CREDIT=true` → COMPENSATED  

### 8) Notifications

```bash
curl -s http://localhost:18085/internal/notifications \
  -H "X-Internal-Api-Key: dev-internal-api-key-change-me"
```

### 9) Rate limit

```bash
for i in $(seq 1 8); do
  curl -s -o /dev/null -w "$i %{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"nope","password":"bad"}'
done
# expect 429 RATE_LIMITED after several attempts
```

### 10) Saga compensate

```bash
SAGA_FAIL_CREDIT=true docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
# transfer → COMPENSATED
SAGA_FAIL_CREDIT=false docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
```

### Local compile (low RAM — no Docker image build)

```bash
cd backend && mvn -T 1C compile -DskipTests
cd frontend/bank-angular-app && npm run lint
```

### CI

`.github/workflows/ci.yml` — backend `mvn verify`, FE lint+build, refuse committed `.env`.

### Push to GitHub (this laptop → main PC)

```bash
# 1) Confirm secrets not staged
git status
git check-ignore -v infra/.env   # must say ignored

# 2) Never: git add infra/.env
# 3) Push framework
git add .
git status   # re-check: no .env
git commit -m "Bank system framework: phases 1-8 + env externalization"
git remote add origin <your-github-url>   # first time
git push -u origin main
```

On main PC:

```bash
git clone <url> && cd bank-system
cp infra/.env.example infra/.env   # fill secrets
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

---

## Docs (SSOT)

| Path | Content |
|------|---------|
| `AGENTS.md` | Rules for AI/dev |
| `docs/00-overview/` | Scope, roadmap, PROVIDE_LATER |
| `docs/01-architecture/` | Architecture, saga, security, diagrams |
| `docs/04-services/*/IMPLEMENT.md` | Per-service prompts |
| `docs/05-frontend/` | Portals, IA, RBAC |
| `docs/06-infra/` | Compose, observability |
| `docs/07-devops/` | CI |
| `docs/08-phases/` | Phases 0–8 |
| `docs/DEMO_SCRIPT.md` | Live demo |
| `docs/INTERVIEW_TALKING_POINTS.md` | Interview |
| `docs/KNOWN_LIMITATIONS.md` | Gaps |

## Phase roadmap

0 Docs → 1 Infra → 2 Auth → 3 Customer/Account → 4 Saga → 5 Notification → 6 Angular → 7 Ops/CI → **8 Demo** ✅

## Stack

Java 21 · Spring Boot 3.3 · Spring Cloud 2023.0 · PostgreSQL · Redis · Kafka · Eureka · Gateway · Micrometer/Zipkin · Prometheus · Grafana · Angular 19 · NgRx · Material

## Known limitations

See **[`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md)** — demo balances, mock notify, HS256, single-node infra, etc.
