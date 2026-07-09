# IMPLEMENT — Infra docker-compose

## Path

`infra/docker-compose.yml` — **single source** (ADR-001)  
Không duy trì `backend/docker-compose.yml` trùng.

## Services list

1. postgres (+ init-databases.sql)
2. redis
3. kafka (KRaft mode preferred)
4. zipkin
5. prometheus (optional scrape)
6. grafana (optional)
7. discovery-server
8. api-gateway
9. auth-service
10. customer-service
11. account-service
12. transaction-service
13. notification-service

## Networks

`bank-net` bridge

## Volumes

- `pgdata`
- `redisdata`
- `prometheusdata`
- `grafanadata`
- service logs optional

## Observability ports

| Service | Host port |
|---------|-----------|
| Zipkin | 9411 |
| Prometheus | 9090 |
| Grafana | 3000 |

## Databases (new machine)

| Step | How |
|------|-----|
| First `compose up` empty volume | Auto: `postgres/init-databases.sql` |
| Volume already exists / fix missing DBs | `./infra/scripts/init-databases.sh` |
| Tables / schema | Flyway `V1__init.sql` mỗi service khi start |

DBs: `bank_auth`, `bank_customer`, `bank_account`, `bank_transaction`, `bank_notification`.

## Env file

`infra/.env.example` → copy `.env`

```
JWT_SECRET=...
AES_SECRET_KEY=...
INTERNAL_API_KEY=...
POSTGRES_USER=bank
POSTGRES_PASSWORD=bank
```

## Healthchecks

DB ready before services; Eureka before gateway; Kafka before transaction/notification.

## Commands

```bash
docker compose -f infra/docker-compose.yml up -d --build
docker compose -f infra/docker-compose.yml logs -f transaction-service
docker compose -f infra/docker-compose.yml down -v
```

## Observability mounts

- `infra/prometheus/prometheus.yml`
- `infra/grafana/provisioning/` (optional)
- Zipkin URL in Spring: `management.tracing.sampling.probability=1.0`

## Resource note

Laptop 16GB: OK. 8GB: drop grafana/prometheus first.
