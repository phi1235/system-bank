# Interview Talking Points

Câu hỏi thường gặp + cách trả lời gắn **code/docs thật trong repo**.

---

## 1. Vì sao microservices, không monolith?

**Trả lời ngắn:** Ranh giới domain banking rõ (auth / profile / ledger / transfer / notify); deploy & scale độc lập; portfolio chứng minh distributed patterns.

**Trade-off:** Phức tạp vận hành, consistency khó hơn, latency network — MVP chấp nhận vì mục tiêu học + demo pattern.

**Chỉ vào:** `docs/01-architecture/architecture.md`, ADR-002 (7 services).

---

## 2. Database-per-service — không join chéo?

- Mỗi service schema/DB riêng: `bank_auth`, `bank_customer`, `bank_account`, `bank_transaction`, `bank_notification` (1 Postgres instance, multi-DB — ADR-007).
- Liên kết **logical** bằng `userId` UUID, không foreign key cross-DB.
- Cần data service khác → **Feign** (sync) hoặc **Kafka** (async).

---

## 3. Saga transfer — điểm wow

**Pattern:** Orchestration (không choreography full) trong `transaction-service`.

```
PENDING → DEBIT source (Feign) → DEBITED
       → CREDIT dest (Feign) → COMPLETED + outbox completed event
Credit fail sau debit → refund source → COMPENSATED
```

**Vì sao không 2PC/XA?** 2PC khóa distributed, khó scale, coupling broker; Saga chấp nhận eventual consistency + compensate tường minh.

**Idempotency:** Header `Idempotency-Key` — replay cùng key trả order cũ, không double debit.

**Code:** `TransferSagaOrchestrator`, `TransferService`, account `debit`/`credit` idempotent theo `referenceId`.

**Docs:** `docs/01-architecture/saga-transfer.md`, ADR-005 (debit/credit, không hold phức tạp).

---

## 4. Transactional Outbox

**Vấn đề:** Ghi DB transfer success rồi publish Kafka fail → notification mất; hoặc publish trước DB fail → event “ma”.

**Outbox:** Cùng transaction với business row → bảng `outbox_events` → `OutboxPoller` publish Kafka → mark sent.

**Topics:** `bank.transaction.completed` / `bank.transaction.failed`.

**Consumer:** `notification-service` + `processed_events` skip duplicate `eventId`.

---

## 5. Security

| Layer | Cách làm |
|-------|----------|
| AuthN | JWT HS256 access + refresh; refresh/blacklist Redis |
| MFA | TOTP (`dev.samstevens.totp`); secret AES at rest |
| AuthZ | Roles CUSTOMER / ADMIN; gateway verify JWT → headers; admin endpoints check role |
| Gateway | JWT filter, block `/internal/**` từ ngoài, CORS, rate limit Redis |
| PII | `nationalId` AES-GCM (`CryptoUtils`), mask khi response |
| Secrets | Env only; `.env` gitignored; CI secrets-check |
| Login abuse | Auth: fail counter lock; Gateway: 5 login/min/IP |

**Không claim:** PCI-DSS, HSM, mTLS service mesh.

---

## 6. Hai portal Frontend

- **Internet Banking:** header/footer, navy/teal — CUSTOMER.  
- **Back Office:** left nav slate/amber — ADMIN.  
- Lazy routes, NgRx (auth / accounts / transfers), interceptors Bearer + refresh 401.  
- RBAC matrix FE mock; backend MVP `ROLE_ADMIN` phẳng (roadmap permissions[]).

**Docs:** `PORTALS.md`, `RBAC.md`, `INFORMATION_ARCHITECTURE.md`.

---

## 7. Observability

- **Zipkin:** Micrometer Tracing — demo 1 transfer cross-service.  
- **Prometheus + Grafana:** `/actuator/prometheus`, scrape compose.  
- **Logs:** pattern có `traceId`/`spanId`.  
- **Không ELK** (scope MVP).

---

## 8. Resilience

- OpenFeign + **Resilience4j** circuit breaker / retry trên call account.  
- Gateway rate limit fail-open nếu Redis down (availability).  
- Healthchecks compose `depends_on` condition healthy.

---

## 9. “Bạn scale thế nào?”

1. Stateless services sau gateway → scale replicas (Eureka).  
2. Kafka partition consumer group cho notification.  
3. DB: tách instance/schema thật khi load (hiện multi-DB 1 node demo).  
4. Outbox poller → có thể chuyển CDC (Debezium) sau.  
5. Không scale shared in-memory state (token Redis).

---

## 10. “Làm lại gì nếu có 3 tháng production?”

Xem `KNOWN_LIMITATIONS.md` — tóm tắt:

- Real email/SMS provider  
- RS256 / key rotation  
- Staff RBAC permissions thật  
- Money với precision/audit chuẩn bank  
- Chaos test saga  
- K8s + secret manager  
- Contract tests / e2e CI  

---

## Câu hỏi ngược cho interviewer (tùy chọn)

1. Team các bạn prefer choreography hay orchestration cho payment?  
2. Outbox vs CDC — production các bạn chọn gì?  
3. Boundary account vs ledger service có tách không?

---

## Cheat sheet path

| Chủ đề | Path |
|--------|------|
| Architecture | `docs/01-architecture/architecture.md` |
| Saga | `docs/01-architecture/saga-transfer.md` |
| Security | `docs/01-architecture/security.md` |
| ADRs | `docs/99-decisions/` |
| Demo steps | `docs/DEMO_SCRIPT.md` |
| Provide real keys | `docs/00-overview/PROVIDE_LATER.md` |
