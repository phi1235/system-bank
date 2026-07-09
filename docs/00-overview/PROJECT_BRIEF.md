# Project Brief — Bank System MVP

## Mục tiêu

Xây **portfolio microservices banking** đủ “wow” khi phỏng vấn:

1. Chứng minh hiểu **distributed systems** (DB-per-service, Saga, event-driven)
2. Chứng minh **security** banking-lite (JWT, MFA, RBAC, encrypt PII, rate limit, audit)
3. Chứng minh **observability + DevOps** cơ bản (Zipkin, Actuator, Docker, CI)
4. Frontend Angular **đúng kiểu enterprise** (NgRx, interceptor, lazy load)

## Không phải mục tiêu

- Production-ready core banking
- Compliance PCI-DSS / NHNN đầy đủ
- Multi-region HA, multi-currency phức tạp
- Mobile app native

## Persona

| Role | Use case |
|------|----------|
| CUSTOMER | Đăng ký/đăng nhập MFA, xem profile, xem tài khoản, chuyển khoản, nhận thông báo |
| ADMIN | Xem danh sách khách hàng, khóa/mở tài khoản, xem audit giao dịch (đơn giản) |

## Success criteria (demo 10–15 phút)

1. `docker compose up` → toàn bộ stack xanh
2. Login + MFA TOTP
3. Customer chuyển khoản thành công; fail mid-way → **compensate** (Saga)
4. Notification mock (log/email file) khi transfer complete
5. Swagger + Zipkin trace cho 1 request transfer
6. Admin khóa account → transfer bị từ chối

## Constraints

- 1 người / AI pair — **7 service, không phình**
- Thời gian gợi ý: **4–6 tuần part-time** (xem ROADMAP)
- Mọi design chốt trong `docs/`; code bám docs

## Review thiết kế ban đầu — KẾT LUẬN

| Hạng mục | Đánh giá | Ghi chú |
|----------|----------|---------|
| 7 service | ✅ Đủ ấn tượng | Bỏ card-service, config-server ở MVP đúng |
| Gateway + Eureka | ✅ | Config-server optional sau |
| Saga transfer | ✅ Điểm wow | Phải có Outbox + idempotency |
| Kafka notification | ✅ | Đơn giản hóa: 1–2 topics |
| Angular NgRx | ✅ | Chỉ auth + accounts + transfer state, đừng over-NgRx |
| docker-compose 2 chỗ | ⚠️ | **Chỉ 1 file** `infra/docker-compose.yml` (ADR-001) |
| common-lib | ✅ | DTO/error/security shared cẩn thận version |
| AES CMND | ✅ | Key từ env, không commit |
| Full ELK | ❌ Out | Log file + volume đủ MVP |

→ **Cấu trúc phân tích của bạn: ĐÃ ỔN** để làm plan. Repo này khóa plan thành file để không drift.
