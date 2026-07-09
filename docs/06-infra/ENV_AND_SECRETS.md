# Environment variables & secrets

## Rules

1. **Secrets never in source YAML defaults** — `JWT_SECRET`, `AES_SECRET_KEY`, `INTERNAL_API_KEY`, `PASSWORD_PEPPER`, `ADMIN_PASSWORD`, `POSTGRES_PASSWORD`, `GRAFANA_ADMIN_PASSWORD` are `${VAR}` only.
2. **Committed template:** `infra/.env.example` (placeholders `change-me-...`).
3. **Local real values:** `infra/.env` — **gitignored**.
4. Compose always:  
   `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d`

## New machine (clone from GitHub)

```bash
git clone <your-repo-url> bank-system
cd bank-system
cp infra/.env.example infra/.env
# Edit infra/.env — replace every change-me-* value
# Generate tips:
#   openssl rand -base64 32   # AES_SECRET_KEY
#   openssl rand -hex 32      # PASSWORD_PEPPER / INTERNAL_API_KEY
#   openssl rand -base64 48   # JWT_SECRET

docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
./infra/scripts/init-databases.sh   # if DBs missing
```

## Variable catalog

| Variable | Used by | Secret? |
|----------|---------|---------|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Postgres + all DB clients | password yes |
| `JWT_SECRET` | auth, gateway | **yes** |
| `AES_SECRET_KEY` | auth MFA, customer PII | **yes** |
| `INTERNAL_API_KEY` | account/customer/txn/notification internal APIs | **yes** |
| `PASSWORD_PEPPER` | auth bound password hash | **yes** |
| `ADMIN_*` | auth seed bootstrap | password yes |
| `ADMIN_SEED_ENABLED` | auth | no |
| `CORS_ORIGINS` | gateway | no |
| `RATE_LIMIT_*` | gateway | no |
| `SAGA_FAIL_CREDIT` | transaction | no (demo) |
| `ACCOUNT_INITIAL_BALANCE` / `ACCOUNT_MAX_PER_USER` | account | no |
| `ZIPKIN_ENDPOINT` | all services | no |
| `GRAFANA_ADMIN_*` | grafana | password yes |
| `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES` | auth | no |
| `JWT_*_TTL` | auth | no |

## Local Maven (without full Docker rebuild)

```bash
set -a && source infra/.env && set +a
# override hosts for host network:
export DB_HOST=localhost DB_PORT=5433 REDIS_HOST=localhost
export EUREKA_URL=http://localhost:8761/eureka/
export KAFKA_BOOTSTRAP=localhost:9092
export ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans
cd backend && mvn -pl auth-service -am spring-boot:run
```

Or create gitignored `application-local.yml` per service (optional).

## Frontend

Public config only in `src/environments/environment.ts` (API URL).  
No secrets in FE. Optional local override: `environment.local.ts` (gitignored).

## GitHub checklist before push

- [ ] `infra/.env` not staged (`git status` / `git check-ignore infra/.env`)
- [ ] No real passwords in commits (`git log -p` if unsure)
- [ ] Only `infra/.env.example` committed
- [ ] `.gitignore` covers `.env`, `**/.env`, `application-local.yml`
