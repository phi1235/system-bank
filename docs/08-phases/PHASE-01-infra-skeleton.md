# PHASE 01 — Infra + skeleton

## Read before code

- `docs/06-infra/docker-compose.md`
- `docs/04-services/common-lib/IMPLEMENT.md`
- `docs/04-services/discovery-server/IMPLEMENT.md`
- `docs/04-services/api-gateway/IMPLEMENT.md` (routes stub OK)
- `docs/07-devops/repo-structure-target.md`

## Checklist

- [x] Parent Maven `backend/pom.xml` + modules (common-lib, discovery, gateway)
- [x] common-lib jar buildable + unit tests
- [x] discovery-server runs
- [x] api-gateway runs (JWT filter, CORS, block `/internal/**`, correlation id)
- [x] postgres + redis + kafka + zipkin in compose
- [x] init-databases.sql
- [x] `.env.example`
- [x] README quick start

## Status

**DONE** (2026-07-09) — local verify: Eureka `:8761` 200, Gateway `/actuator/health` UP, `/internal/**` → 403, protected path → 401.

## Exit

`docker compose up` → Eureka UI up, Gateway 8080 responds. ✅
