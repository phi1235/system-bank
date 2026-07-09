# Architecture diagrams (Mermaid)

Export PNG optional: paste vào [mermaid.live](https://mermaid.live) → Download PNG/SVG cho slide.

## 1. System context

```mermaid
flowchart LR
  subgraph Clients
    C[Customer Browser<br/>Angular IB]
    A[Admin Browser<br/>Angular BO]
  end

  GW[API Gateway<br/>JWT · CORS · RateLimit]

  subgraph Services
    AUTH[auth-service]
    CUST[customer-service]
    ACC[account-service]
    TXN[transaction-service]
    NOTI[notification-service]
  end

  EU[Eureka]
  PG[(PostgreSQL multi-DB)]
  RD[(Redis)]
  KF{{Kafka}}
  ZK[Zipkin]
  PR[Prometheus]

  C --> GW
  A --> GW
  GW --> AUTH & CUST & ACC & TXN
  TXN -->|Feign debit/credit| ACC
  TXN -->|Outbox poll| KF
  KF --> NOTI
  AUTH & CUST & ACC & TXN & NOTI & GW --> EU
  AUTH & CUST & ACC & TXN & NOTI --> PG
  AUTH & GW --> RD
  AUTH & CUST & ACC & TXN & NOTI & GW -.-> ZK
  AUTH & CUST & ACC & TXN & NOTI & GW -.-> PR
```

## 2. Transfer saga (happy + compensate)

```mermaid
sequenceDiagram
  participant FE as Angular
  participant GW as Gateway
  participant TX as transaction-service
  participant AC as account-service
  participant OB as Outbox+Kafka
  participant NT as notification-service

  FE->>GW: POST /transfers + Idempotency-Key
  GW->>TX: JWT → X-User-Id
  TX->>TX: Validate + create TransferOrder PENDING
  TX->>AC: Feign DEBIT source
  AC-->>TX: OK balance
  TX->>AC: Feign CREDIT dest
  alt credit OK
    AC-->>TX: OK
    TX->>TX: status COMPLETED
    TX->>OB: outbox COMPLETED
    OB->>NT: bank.transaction.completed
    NT->>NT: MOCK_EMAIL + notification_logs
  else credit fail
    AC-->>TX: error
    TX->>AC: Feign CREDIT source (refund)
    TX->>TX: status COMPENSATED
    TX->>OB: outbox FAILED
    OB->>NT: bank.transaction.failed
  end
  TX-->>FE: TransferResponse status
```

## 3. Auth / token flow

```mermaid
sequenceDiagram
  participant FE
  participant GW
  participant AUTH
  participant RD as Redis

  FE->>GW: POST /auth/login
  GW->>AUTH: forward
  AUTH->>RD: check lock / store refresh
  alt MFA enabled
    AUTH-->>FE: mfaRequired + mfaToken
    FE->>GW: POST /auth/mfa/verify
    GW->>AUTH: verify TOTP
  end
  AUTH-->>FE: access + refresh JWT
  FE->>GW: API + Bearer access
  GW->>GW: verify JWT
  GW->>AUTH: (routed APIs with X-User-*)
```

## 4. Portal split

```mermaid
flowchart TB
  subgraph IB["Internet Banking /customer"]
    H[Header + Footer]
    H --> Home & Accounts & Transfer & History & Profile
  end

  subgraph BO["Back Office /admin"]
    S[Left module nav]
    S --> Dash & Customers & Freeze & Tx & Audit & RBAC
  end

  LoginC[/auth/login] --> IB
  LoginA[/admin/login] --> BO
```
