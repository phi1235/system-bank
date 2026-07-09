# Database per Service

## Nguyên tắc

- Mỗi service **chỉ** connect DB của mình
- Không foreign key cross-DB
- Reference bằng UUID logical
- Migration: **Flyway** mỗi service (`db/migration/V1__init.sql`)

## Connection (mẫu)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```

## Isolation demo

Compose: 5 Postgres containers **hoặc** 1 Postgres multi-database init script.

**MVP chốt (ADR-007):** **1 Postgres container**, nhiều database (`bank_auth`, …) qua `init-databases.sql` — tiết kiệm RAM laptop, vẫn đúng “logical DB per service”.

```sql
-- infra/postgres/init-databases.sql
CREATE DATABASE bank_auth;
CREATE DATABASE bank_customer;
CREATE DATABASE bank_account;
CREATE DATABASE bank_transaction;
CREATE DATABASE bank_notification;
```

## ER docs

Xem `er-diagrams/*.md` — schema text + mermaid.

## Indexing gợi ý

- auth: unique username/email
- account: unique account_number; index user_id
- transfer: unique idempotency; index user_id, created_at
- outbox: index published_at NULL

## Backup / multi-tenant

Out of scope MVP.
