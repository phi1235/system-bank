# Design Review — Phân tích đề xuất ban đầu

## Kết luận tổng

**Thiết kế của bạn ĐÃ ỔN (~8.5/10)** cho portfolio/phỏng vấn.  
Đã **khóa** thành SSOT trong `docs/` để tránh drift khi AI code.

## Điểm mạnh giữ nguyên

1. 7 service — đủ microservices, không bloat
2. Gateway + Eureka + JWT/MFA/RBAC
3. Saga + Kafka notification — wow đúng chỗ
4. DB-per-service + Redis
5. Angular NgRx + interceptor + lazy load
6. docker-compose full stack + CI + Zipkin
7. Bỏ config-server / card-service ở MVP — đúng ưu tiên

## Chỉnh nhỏ đã chốt (ADR)

| Vấn đề đề xuất gốc | Cách chốt |
|--------------------|-----------|
| 2 docker-compose | 1 file `infra/` (ADR-001) |
| card-service trong tree | Out of MVP (ADR-002) |
| config-server “có thể gộp” | Out MVP (ADR-003) |
| customerId vs userId mơ hồ | `customerId = userId` (ADR-004) |
| Saga hold vs debit | Debit/credit + compensate (ADR-005) |
| Register tạo profile? | Lazy `POST /customers/me` (ADR-006) |
| 5 Postgres containers nặng | 1 Postgres multi-DB (ADR-007) |
| Account balance 0 khó demo | Seed 1_000_000 VND (ADR-008) |
| Material vs Tailwind | Material (ADR-009) |
| Maven vs Gradle | Maven multi-module (ADR-010) |

## Rủi ro còn lại (biết trước)

| Risk | Mức | Xử lý trong plan |
|------|-----|------------------|
| Saga sync trong HTTP request timeout | Trung | Timeout Feign + status FAILED rõ |
| JWT HS256 shared secret | Thấp (demo) | Document; RS256 later |
| Trust gateway headers | Trung | Internal API key + docker network |
| NgRx over-engineering | Thấp | Chỉ 3 slices |
| Effort FE | Trung | Phase 6 sau backend E2E chắc |

## Điểm “ăn phỏng vấn” map → file

| Câu hay bị hỏi | Trả lời từ |
|----------------|------------|
| Distributed transaction? | saga-transfer.md |
| Dual write Kafka? | Outbox trong saga doc |
| Service discovery? | service-map + discovery IMPLEMENT |
| Bảo mật banking lite? | security.md |
| DB isolation? | database-per-service.md |
| FE token refresh? | frontend ARCHITECTURE |

## Không làm gì thêm ở giai đoạn design

- Không generate code production
- Không vẽ Figma
- Không chọn cloud provider

→ Bước tiếp: **human approve** rồi `PHASE-01`.
