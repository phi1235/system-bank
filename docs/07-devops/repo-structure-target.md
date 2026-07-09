# Target repo structure (sau khi code)

```
bank-system/
├── AGENTS.md
├── README.md
├── docs/                          # SSOT design (đã có)
├── backend/
│   ├── pom.xml                    # parent multi-module
│   ├── common-lib/
│   ├── discovery-server/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── customer-service/
│   ├── account-service/
│   ├── transaction-service/
│   └── notification-service/
├── frontend/
│   └── bank-angular-app/
├── infra/
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── postgres/init-databases.sql
│   ├── prometheus/prometheus.yml
│   ├── grafana/
│   └── zipkin/                    # optional config
└── .github/workflows/ci.yml
```

Build tool: **Maven multi-module** (phổ biến phỏng vấn Java) — ADR-010.
