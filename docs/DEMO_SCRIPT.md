# Demo Script — 12–15 phút

> Dùng trên **PC đủ RAM** sau `docker compose up -d --build`.  
> Máy 8GB: chỉ đọc script + show code; không force rebuild.

## Trước demo (5–10 phút setup)

```bash
# Repo root
cp -n infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
# Chờ healthy: gateway, auth, account, transaction, notification
docker ps --format '{{.Names}} {{.Status}}' | sort

cd frontend/bank-angular-app && npm install && npm start
```

Mở tab sẵn:

| Tab | URL |
|-----|-----|
| FE Customer | http://localhost:4200/auth/login |
| FE Admin | http://localhost:4200/admin/login |
| Eureka | http://localhost:8761 |
| Zipkin | http://localhost:9411 |
| Prometheus | http://localhost:9090/targets |
| Terminal | ready for `docker logs` / curl |

**Tài khoản seed**

| User | Password | Role |
|------|----------|------|
| `admin` | `Admin123!` | ADMIN (Back Office) |
| *(register mới)* | `Test1234!` trở lên | CUSTOMER |

---

## Timeline gợi ý

| Phút | Nội dung | Nói gì (1 câu) |
|------|----------|----------------|
| 0:00–1:00 | Architecture + Eureka | “7 service, DB-per-service, gateway JWT” |
| 1:00–3:00 | Register/login customer | “Auth JWT + optional MFA TOTP” |
| 3:00–5:00 | Profile + 2 accounts | “Customer profile lazy; account demo balance 1M VND” |
| 5:00–7:30 | Transfer success + history | “Saga sync DEBIT→CREDIT + Idempotency-Key” |
| 7:30–9:00 | Zipkin + notification log | “Trace distributed + Kafka outbox → mock email” |
| 9:00–11:30 | Fail path (freeze **hoặc** compensate) | “Compensation / business guard” |
| 11:30–13:30 | Admin portal | “Hai portal tách shell + RBAC ADMIN” |
| 13:30–15:00 | Code peek + limitations | “Orchestrator + OutboxPoller; known limits” |

---

## Bước chi tiết

### 1) Compose + Eureka (1 phút)

- Eureka: tất cả instance **UP** (AUTH, CUSTOMER, ACCOUNT, TRANSACTION, NOTIFICATION, GATEWAY).
- Nói: service discovery + load-balanced gateway `lb://…`.

### 2) Customer happy path (4–5 phút)

1. **Register** user mới (username unique).  
2. **Login** → `/customer/home`.  
3. **Profile** → tạo hồ sơ (fullName, phone).  
4. **Accounts** → Mở PAYMENT + SAVINGS (balance demo 1_000_000).  
5. **Chuyển tiền**: from PAYMENT → toAccountNumber SAVINGS, amount e.g. `50000`, confirm.  
6. Status **COMPLETED** + history có dòng mới + balance đổi.

### 3) Observability + notification (2 phút)

1. **Zipkin** → search service `TRANSACTION-SERVICE` / `api-gateway` → 1 trace transfer (gateway → txn → Feign account).  
2. Terminal:
   ```bash
   docker logs bank-notification 2>&1 | grep MOCK_EMAIL | tail -3
   curl -s http://localhost:18085/internal/notifications \
     -H "X-Internal-Api-Key: dev-internal-api-key-change-me" | head -c 500
   ```
3. Nói: Transactional Outbox → Kafka → consumer idempotent (`processed_events`).

### 4A) Fail path — Admin freeze (khuyến nghị, ổn định)

1. Copy **account UUID** nguồn từ FE (Accounts hoặc Network tab).  
2. **Admin login** `admin` / `Admin123!`.  
3. **Accounts** → paste UUID → **Freeze**.  
4. Customer transfer lại → lỗi `ACCOUNT_FROZEN` / 422.  
5. **Unfreeze** nếu cần demo tiếp.

### 4B) Fail path — Saga compensate (optional, cần recreate txn service)

```bash
SAGA_FAIL_CREDIT=true docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
# Chờ healthy → transfer → status COMPENSATED, số dư nguồn hoàn
SAGA_FAIL_CREDIT=false docker compose -f infra/docker-compose.yml --env-file infra/.env \
  up -d --force-recreate --no-deps transaction-service
```

Chỉ làm nếu còn thời gian + máy ổn định.

### 5) Admin extras (1 phút)

- Customers list + KYC Verify  
- Transactions monitor  
- Audit log  
- RBAC matrix (mock roadmap roles)

### 6) Code walk (2 phút) — chỉ 2 file

1. `backend/transaction-service/.../TransferSagaOrchestrator.java`  
   - DEBIT → CREDIT → enqueue completed/failed  
   - `SAGA_FAIL_CREDIT` inject  
2. `backend/transaction-service/.../OutboxPoller.java`  
   - poll outbox → Kafka topics  

Optional: `NotificationHandler.java` idempotent skip.

---

## Curl fallback (nếu FE lỗi)

```bash
GW=http://localhost:8080
TS=$(date +%s); USER="demo$TS"; PASS='Test1234!'

curl -s -X POST $GW/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"email\":\"$USER@test.local\",\"password\":\"$PASS\",\"fullName\":\"Demo\"}"

TOKEN=$(curl -s -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

curl -s -X POST $GW/api/v1/customers/me -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"fullName":"Demo User","phone":"+84900000000"}'

A1=$(curl -s -X POST $GW/api/v1/accounts -H "$AUTH" -H 'Content-Type: application/json' -d '{"accountType":"PAYMENT"}')
A2=$(curl -s -X POST $GW/api/v1/accounts -H "$AUTH" -H 'Content-Type: application/json' -d '{"accountType":"SAVINGS"}')
FROM=$(echo $A1 | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
TO=$(echo $A2 | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accountNumber'])")

curl -s -X POST $GW/api/v1/transactions/transfers -H "$AUTH" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen 2>/dev/null || echo demo-$TS)" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountNumber\":\"$TO\",\"amount\":25000,\"currency\":\"VND\",\"description\":\"Demo script\"}"
```

---

## Backup plans

| Sự cố | Cách xử lý |
|-------|------------|
| Eureka empty | đợi 30–60s; `docker logs bank-discovery` |
| Gateway 503 | service chưa register; gọi direct `:1808x` + header user (nếu biết) hoặc đợi |
| Transfer chậm | outbox poll 1s; poll GET transfer id |
| MFA bật nhầm | dùng user mới chưa enable MFA |
| RAM thấp | skip recreate SAGA_FAIL; chỉ freeze |

---

## Kết thúc demo (30 giây)

> “MVP portfolio: 7 microservices, Saga+Outbox, JWT/MFA, 2 portals Angular, Kafka notification, Zipkin/Prometheus, CI.  
> Không claim production core-banking — xem Known limitations.”

Chi tiết nói chuyện: `docs/INTERVIEW_TALKING_POINTS.md` · hạn chế: `docs/KNOWN_LIMITATIONS.md`.
