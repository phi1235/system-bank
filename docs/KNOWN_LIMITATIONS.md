# Known Limitations — Honest scope

Portfolio MVP. **Không** claim production core-banking / NHNN / PCI.

## Architecture

| Limitation | Hiện tại | Hướng sau |
|------------|----------|-----------|
| 1 Postgres multi-DB | Đủ demo isolation logical | DB instance per service |
| Eureka single node | Compose demo | Managed discovery / K8s |
| Sync saga in request thread | Dễ demo status COMPLETED ngay | Async worker + poll status |
| No distributed lock multi-orchestrator | 1 replica txn service OK | Lock / partition by order id |
| Admin RBAC phẳng `ADMIN` | FE matrix mock | permissions[] + staff users |

## Security

| Limitation | Hiện tại |
|------------|----------|
| JWT HS256 shared secret | Đủ MVP; prod → RS256 + rotation |
| Dev defaults in `.env.example` | **Đổi** trước share/deploy |
| Admin bootstrap seed | Env-driven; set `ADMIN_SEED_ENABLED=false` after first deploy |
| Password over HTTP in local compose | Prod **must** TLS; optional RSA field encrypt (design doc) |
| Roles string CUSTOMER/ADMIN | Dynamic RBAC tables = roadmap (see security-dynamic-design.md) |
| Internal API key static | mTLS / SPIFFE later |
| MFA secret trả plain lúc setup | Dev-only convenience |
| Rate limit fail-open nếu Redis down | Prefer fail-open availability |
| No captcha / device binding | Out of scope |

## Data & money

| Limitation | Hiện tại |
|------------|----------|
| Demo initial balance 1_000_000 | ADR-008 — không phải onboarding thật |
| VND only | No FX |
| BigDecimal qua JSON | Chưa full money library / rounding policy bank |
| No statement PDF / interest | Placeholder FE |

## Messaging & notify

| Limitation | Hiện tại |
|------------|----------|
| Mock email/SMS (log) | `PROVIDE_LATER` provider thật |
| Recipient email synthetic `user-{id}@bank.local` | Enrich từ customer profile |
| Kafka single broker KRaft | Cluster / managed Kafka later |

## Frontend

| Limitation | Hiện tại |
|------------|----------|
| i18n partial (UI VI hardcode) | Full i18n later |
| Confirm transfer = `window.confirm` | Material dialog polish |
| Token sessionStorage | XSS risk — document for interview |
| Placeholder cards/wealth/risk | IA giữ chỗ |

## Ops

| Limitation | Hiện tại |
|------------|----------|
| No CD / K8s | Compose + GH Actions CI only |
| No ELK | Console + Zipkin |
| Full image rebuild nặng (~8GB RAM khó) | Build trên máy mạnh / CI |
| Grafana dashboard minimal | Expand panels later |

## Testing

| Limitation | Hiện tại |
|------------|----------|
| Unit tests rải rác per module | Coverage không 100% |
| No Testcontainers e2e in CI | Manual demo script |
| No contract tests (Pact) | Optional |

## Explicitly out of scope (SCOPE_MVP)

card-service · config-server · GraphQL · mobile · multi-currency · real payment gateway · full AML · event sourcing full.

---

Khi interviewer hỏi “production gap?” → mở file này + 3 cải tiến ưu tiên:

1. Provider notify + secret management  
2. Async saga + stronger ledger audit  
3. Staff RBAC + observability SLOs  
