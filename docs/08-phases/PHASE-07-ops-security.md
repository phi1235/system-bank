# PHASE 07 — Observability + CI + harden

## Checklist

- [x] Zipkin traces (Micrometer + Brave) all services → `ZIPKIN_ENDPOINT`
- [x] Prometheus scrape (`/actuator/prometheus`) + compose service `:9090`
- [x] Grafana provisioned datasource + overview dashboard `:3000`
- [x] GitHub Actions CI (backend verify + FE lint/build + no `.env` committed)
- [x] Rate limit login (5/min/IP Redis) + global 100/min/IP
- [x] Security response headers on gateway
- [x] Audit endpoints (`GET /api/v1/admin/audit-logs`) — already Phase 4, verified in runbook
- [x] Secrets only env (`.env.example`; `.env` gitignored; CI secrets-check)
- [x] README runbook complete

## Status

**DONE** (2026-07-09)

### Observability

| Tool | URL | Notes |
|------|-----|--------|
| Zipkin | http://localhost:9411 | Search by service `TRANSACTION-SERVICE` after transfer |
| Prometheus | http://localhost:9090 | Targets → all services UP |
| Grafana | http://localhost:3000 | admin/admin (env) · dashboard *Bank System — JVM / HTTP* |

Env: `ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans` (compose)

### Rate limit

- `POST /api/v1/auth/login` → Redis key `rl:login:{ip}`, default **5 / 60s** → HTTP 429 `RATE_LIMITED`
- Global `rl:global:{ip}` → **100 / 60s** (configurable)

### CI

`.github/workflows/ci.yml` — JDK 21 `mvn verify` · Node 20 FE `lint`+`build` · fail if `.env` tracked

### Secrets

Only `infra/.env.example`. Real secrets in `infra/.env` (gitignored). See `docs/00-overview/PROVIDE_LATER.md`.

### Rollout note

Gateway image already includes rate-limit + `/actuator/prometheus`.  
To enable Zipkin + Prometheus on **all** services after pull:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Verify:

```bash
# login rate limit → 429 RATE_LIMITED after ~5–6 POSTs
# metrics
curl -s http://localhost:8080/actuator/prometheus | head
# prometheus UI targets: http://localhost:9090/targets
# zipkin after transfer: http://localhost:9411
```

## Exit

Clone → follow README → demo works on clean machine (docker). ✅

Next: **Phase 8** — Demo / interview (`PHASE-08-demo-interview.md`)
