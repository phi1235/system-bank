# PHASE 08 — Demo & interview pack

## Deliverables

- [x] `docs/DEMO_SCRIPT.md` — 12–15 phút (UI + curl fallback)
- [x] `docs/INTERVIEW_TALKING_POINTS.md` — saga, outbox, MFA, DB-per-service, scale
- [x] Architecture diagrams Mermaid — `docs/01-architecture/architecture-diagram.md` (export PNG optional mermaid.live)
- [x] Sample users table in README
- [x] Known limitations — `docs/KNOWN_LIMITATIONS.md`
- [x] README portfolio links + phase status

## Demo order (summary)

1. Compose up + Eureka  
2. FE customer register/login  
3. Accounts + transfer success  
4. Zipkin trace + notification MOCK_EMAIL  
5. Admin freeze **or** SAGA_FAIL_CREDIT compensate  
6. Admin audit / KYC  
7. Code: `TransferSagaOrchestrator` + `OutboxPoller`  

Full steps: **`docs/DEMO_SCRIPT.md`**

## Status

**DONE** (2026-07-09) — docs pack only (no Docker rebuild required on low-RAM machine).

### Portfolio file index

| File | Use |
|------|-----|
| `README.md` | Clone → run |
| `docs/DEMO_SCRIPT.md` | Live demo |
| `docs/INTERVIEW_TALKING_POINTS.md` | Q&A |
| `docs/KNOWN_LIMITATIONS.md` | Honesty |
| `docs/01-architecture/architecture-diagram.md` | Slides |
| `docs/00-overview/PROVIDE_LATER.md` | Real secrets/providers later |

## Exit

Repo ready portfolio link. ✅

Phases 0–8 complete for MVP scope.
