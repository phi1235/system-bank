# System Bank - Enterprise Microservices Banking Platform

[![Java](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0-blue.svg?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-cloud)
[![Angular](https://img.shields.io/badge/Angular-19-DD0031.svg?style=for-the-badge&logo=angular)](https://angular.dev/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-black.svg?style=for-the-badge&logo=apachekafka)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1.svg?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose_v2-2496ED.svg?style=for-the-badge&logo=docker)](https://www.docker.com/)

---

## Executive Summary

**System Bank** is a production-grade, distributed microservices platform designed for modern **Internet Banking (Customer Portal)** and **Back-Office Operations (Admin Portal)**.

The system demonstrates enterprise architectural patterns, high-concurrency transaction processing, event-driven async notification pipelines, zero-trust perimeter security, and end-of-day financial reconciliation—built strictly adhering to **Clean Architecture** and **Domain-Driven Design (DDD)** principles.

---

## System Architecture Overview

```
                                      ┌───────────────────────────────────────┐
                                      │        Internet & Admin Clients       │
                                      └──────────────────┬────────────────────┘
                                                         │ HTTPS
                                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       API Gateway (Port 8080)                                           │
│                 [ JWT Auth Validation · Redis Rate Limiting · HMAC Downstream Signing ]                   │
└─────────┬───────────────────┬───────────────────┬───────────────────┬───────────────────┬───────────────┘
          │                   │                   │                   │                   │
          ▼                   ▼                   ▼                   ▼                   ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│   Auth Service   │ │ Customer Service │ │ Account Service  │ │Transaction Service│ │ Eureka Discovery │
│ (Auth & TOTP MFA)│ │ (Profile & KYC)  │ │(Ledger & Balances│ │ (Saga Orchestrator│ │ (Service Registry│
└─────────┬────────┘ └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
          │                   │                   │                   │                   │
          ▼                   ▼                   ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                PostgreSQL 16 (Logical DB-per-Service)                                   │
│            bank_auth   ·   bank_customer   ·   bank_account   ·   bank_transaction  ·  bank_notification│
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                           │
                                             Outbox Event  ▼
                                      ┌─────────────────────────┐
                                      │   Apache Kafka Broker   │
                                      └────────────┬────────────┘
                                                   │
                                                   ▼
                                      ┌─────────────────────────┐
                                      │  Notification Service   │
                                      │  (Email / SMS Dispatch) │
                                      └─────────────────────────┘
```

### Microservices Ecosystem

| Microservice | Primary Responsibility | Key Features & Design Patterns |
| :--- | :--- | :--- |
| **api-gateway** | Edge Routing & Security | JWT validation, Redis rate limiting, CORS, HMAC-SHA256 identity header signing |
| **discovery-server** | Service Discovery | Netflix Eureka registry with real-time heartbeat health checks |
| **auth-service** | Identity & Access | Customer/Admin authentication, Refresh Tokens, MFA TOTP, Peppered BCrypt, Admin Seed |
| **customer-service** | Customer Management | Customer profiles, KYC approval workflow, AES-GCM encrypted PII storage |
| **account-service** | Core Ledger & Accounts | CASA accounts, balance management, transaction ledger, account freeze/unfreeze |
| **transaction-service** | Transfer Engine | Internal transfers, **Idempotency**, **Saga Orchestrator**, **Outbox Event Publisher**, **EOD Reconciliation** |
| **notification-service** | Asynchronous Messaging | Kafka event consumers, idempotent message processing, mock Email/SMS delivery logger |

---

## Tech Stack & Specifications

### Backend Ecosystem
- **Core Runtime:** Java 21 (LTS), Spring Boot 3.3.x, Spring Cloud 2023.0.x
- **Service Discovery & Inter-Service Communication:** Spring Cloud Netflix Eureka, OpenFeign, Resilience4j Circuit Breakers
- **Data Access & Persistence:**
  - **Writes & Domain Logic:** Spring Data JPA / Hibernate (Database-per-Service isolation)
  - **Analytical & EOD Reporting:** MyBatis for high-performance read-model queries
  - **Schema Migrations:** Flyway versioned SQL scripts (`src/main/resources/db/migration`)
- **Messaging & Caching:** Apache Kafka 3.7 (Event-Driven Architecture), Redis 7 (Token Blacklist & Rate Limiting)
- **Distributed Tracing & Metrics:** Micrometer, Zipkin, Prometheus, Grafana

### Frontend Ecosystem
- **Framework:** Angular 19, RxJS, NgRx State Management
- **UI Components & Styling:** Angular Material, Custom Responsive SCSS Design System
- **Internationalization (i18n):** `ngx-translate` supporting **Vietnamese (`vi`)** and **English (`en`)** seamlessly across all templates (strictly no hardcoded strings)

### DevOps & Infrastructure
- **Containerization:** Multi-stage Docker builds utilizing BuildKit Maven layer caching
- **Orchestration:** Docker Compose v2 (Production & Development profiles)
- **CI/CD Pipeline:** Hybrid setup featuring GitHub Actions (PR validation gate) and Jenkins Pipeline (`Jenkinsfile` for Docker packaging & automated verification)

---

## Key Architectural & Engineering Highlights

### 1. Saga Pattern for Distributed Transactions
Financial transfers spanning separate microservices (`account-service` debit/credit) execute using an **Orchestrated Saga Pattern**:
1. **Debit Request:** `transaction-service` issues a debit instruction to the source account in `account-service`.
2. **Credit Request:** Upon successful debit, it issues a credit instruction to the destination account.
3. **Compensating Rollback:** If crediting fails (e.g., recipient account closed/frozen), the Saga Orchestrator triggers an automatic **Compensating Transaction** to refund the source account, restoring financial consistency without distributed 2PC locks.

### 2. Transactional Outbox Pattern
To guarantee **at-least-once event delivery** without dual-write inconsistencies between Postgres and Kafka:
- Business state and notification events are saved inside the same database transaction into an `outbox` table.
- A background worker reads unprocessed outbox records, publishes them to Apache Kafka, and marks them as processed upon ACK.
- Consumers in `notification-service` process events idempotently using message deduplication.

### 3. Multi-Layered Security & Zero-Trust Architecture
- **Edge Token Verification:** API Gateway inspects and validates JWT signatures (`HS256`).
- **Downstream Identity Signing:** Gateway strips raw caller headers and injects internal identity claims signed with **HMAC-SHA256**. Downstream microservices verify HMAC signatures before trusting caller metadata.
- **Data Protection at Rest:** Sensitive PII (National ID, Phone numbers) and MFA TOTP secrets are encrypted using **AES-256-GCM**.
- **Password Security:** Multi-pass hashing using **BCrypt** combined with a server-side **Pepper** (`PASSWORD_PEPPER`).
- **Network Isolation:** Microservices operate strictly within a private Docker internal network (`bank-net`). Direct backend ports are not exposed publicly.

### 4. End-Of-Day (EOD) Financial Reconciliation Engine
- Automatically runs scheduled or manual EOD reconciliation jobs.
- Compares `transfer_orders` from `transaction-service` against raw ledger entries pulled from `account-service`.
- Identifies and flags discrepancies (amount mismatches, uncompleted in-flight orders, missing debit/credit legs) without requiring cross-database SQL joins across isolated databases.

---

## Feature Matrix

### Customer Portal (Internet Banking)
- **Authentication:** Secure Register & Login with optional MFA TOTP (Google Authenticator / Authy).
- **Account Dashboard:** View active CASA accounts, real-time balances, and account details.
- **Money Transfers:** Internal transfers with **Idempotency-Key** protection against accidental double submissions.
- **Transaction History:** Detailed statement view with status filtering and multi-language support.

### Admin Back-Office Portal
- **Staff Access:** Bootstrapped secure admin authentication.
- **Customer Directory & KYC:** Manage customer profiles, review KYC documents, update verification status.
- **Account Operations:** Freeze / unfreeze customer accounts instantly.
- **Audit & Monitoring:** Real-time audit logs, transaction monitoring dashboard, and MyBatis-powered financial reports.
- **EOD Reconciliation Management:** Trigger and review daily financial reconciliation balance reports.

---

## API Exposure & Port Directory

All external requests enter strictly via the **API Gateway** on host port `8080` (or `API_GATEWAY_HOST_PORT`).

| Component / Service | Host Port Variable | Default Host Access | Internal Network Port |
| :--- | :--- | :--- | :--- |
| **API Gateway** | `API_GATEWAY_HOST_PORT` | `http://localhost:8080` | `8080` |
| **Frontend Portal (Angular)** | - | `http://localhost:4200` | N/A |
| **PostgreSQL** | `POSTGRES_HOST_PORT` | `localhost:5432` *(Dev only)* | `5432` |
| **Redis** | `REDIS_HOST_PORT` | `localhost:6379` *(Dev only)* | `6379` |
| **Kafka Broker** | `KAFKA_HOST_PORT` | `localhost:9092` *(Dev only)* | `9092` |
| **Zipkin Tracing** | `ZIPKIN_HOST_PORT` | `localhost:9411` *(Dev only)* | `9411` |
| **Prometheus** | `PROMETHEUS_HOST_PORT` | `localhost:9090` *(Dev only)* | `9090` |
| **Grafana Dashboard** | `GRAFANA_HOST_PORT` | `localhost:3000` *(Dev only)* | `3000` |

*Note: Core backend services (`auth`, `customer`, `account`, `transaction`, `notification`, `discovery`) do not publish public ports; they are accessible only via Gateway edge routing `/api/v1/...`.*

---

## Quick Start & Deployment Guide

### Prerequisites
- **Docker & Docker Compose v2** installed.
- **JDK 21** & **Maven 3.9+** (for local host development).
- **Node.js 20+** & **npm 10+** (for frontend development).
- **Recommended System Specs:** ≥ 16 GB RAM.

---

### 1. Environment Setup
Clone the repository and prepare the environment configuration:

```bash
git clone https://github.com/phi1235/system-bank.git
cd system-bank

# Copy environment template
cp infra/.env.example infra/.env
```

Generate secure random keys for your `infra/.env` file:
```bash
openssl rand -base64 32                  # AES_SECRET_KEY
openssl rand -hex 32                     # GATEWAY_SIGNING_SECRET & PASSWORD_PEPPER
openssl rand -base64 48                  # JWT_SECRET
openssl rand -base64 16 | tr -d '='      # KAFKA_CLUSTER_ID
```

---

### 2. Launch Infrastructure & Microservices via Docker

**Option A: Full Microservices Stack (Recommended)**
```bash
# Validate compose environment
docker compose -f infra/docker-compose.yml --env-file infra/.env config --quiet

# Build and start all services in detached mode
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

**Option B: Infrastructure Only (For local IDE debugging)**
```bash
# Start Postgres, Redis, Kafka, Zipkin, Prometheus, Grafana
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d

# Run any backend service locally (e.g. auth-service)
cd backend
mvn -pl auth-service -am spring-boot:run
```

---

### 3. Launch Frontend (Angular Application)

```bash
cd frontend/bank-angular-app
npm install
npm start
```

Access the applications in your web browser:
- **Internet Banking Portal:** `http://localhost:4200/auth/login`
- **Admin Back-Office Portal:** `http://localhost:4200/admin/login`

---

### 4. Verification & Health Checks

Verify API Gateway health status:
```bash
curl -s http://localhost:8080/actuator/health
```

Expected output:
```json
{"status":"UP"}
```

---

## Demonstration Workflows & Scenarios

### 1. Money Transfer Happy Path
1. Access `http://localhost:4200/auth/login` and register a new customer account.
2. Complete profile setup and open two CASA accounts (Account A and Account B).
3. Execute an internal transfer from Account A to Account B.
4. Verify immediate balance updates and transfer history logs.

### 2. Saga Compensation Rollback Test
To simulate a credit failure and observe automatic refund compensation:
```bash
# Force credit failure mode in transaction-service
SAGA_FAIL_CREDIT=true docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
```
1. Perform a transfer via UI.
2. Transaction status moves to `COMPENSATED`, and funds are safely returned to Account A.
3. Reset failure mode: `SAGA_FAIL_CREDIT=false`.

### 3. Login Rate Limiting Demo
```bash
# Execute rapid invalid login attempts to trigger Redis Rate Limiter
for i in {1..10}; do
  curl -s -o /dev/null -w "Attempt $i: %{http_code}\n" \
    -X POST "http://localhost:8080/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"baduser","password":"wrongpassword"}'
done
# Expect HTTP 429 (Too Many Requests) after threshold limit
```

---

## Repository Directory Structure

```
system-bank/
├── .github/                 # GitHub Actions Workflows (PR Gate CI)
├── backend/                 # Maven Multi-Module Project Root
│   ├── common-lib/          # Shared DTOs, Security Filters, Exception Handlers
│   ├── discovery-server/   # Eureka Service Registry
│   ├── api-gateway/         # Spring Cloud Gateway & Auth Filters
│   ├── auth-service/        # Authentication, JWT & TOTP Service
│   ├── customer-service/    # Customer Profile & KYC Service
│   ├── account-service/     # Ledger, CASA Accounts & Balances
│   ├── transaction-service/ # Saga Engine, Outbox & EOD Reconciliation
│   └── notification-service/# Kafka Event Consumers & Delivery Loggers
├── frontend/
│   ├── bank-angular-app/    # Angular 19 Enterprise Web Application
│   └── ui-mockups/          # Static UI/UX Prototype Mockups
├── docs/                    # Architecture & CI/CD Documentation
├── infra/                   # DevOps & Infrastructure Configurations
│   ├── jenkins/             # Jenkins JCasC & Docker setup
│   ├── postgres/            # Database init SQL scripts
│   ├── prometheus/          # Prometheus metrics scrapers
│   ├── grafana/             # Monitoring dashboards
│   ├── docker-compose.yml   # Main Stack Docker Compose
│   └── .env.example         # Environment variable template
└── Jenkinsfile              # Enterprise CI/CD Pipeline Definition
```

---

## Security & Compliance Standard

- **Zero Hardcoded Secrets:** All secrets, keys, and credentials are read strictly from environment variables.
- **OWASP Top 10 Mitigated:** SQL Injection prevented via JPA/MyBatis parameter bindings, XSS sanitized, CSRF & CORS configured.
- **PII Protection:** AES-256-GCM encryption applied to all sensitive personal identifiable information at rest.
- **Git Hygiene:** Strict `.gitignore` rules enforced to prevent accidental commits of `.env`, keystores, or build artifacts.

---

## License & Disclaimers

This project is created for **educational, architectural demonstration, and portfolio purposes only**.  
It is **not** intended for live commercial core-banking operation without further regulatory compliance and auditing.

---

<p align="center">
  <b>Developed by phi1235</b> • Built with Clean Code & Distributed Systems Engineering
</p>
