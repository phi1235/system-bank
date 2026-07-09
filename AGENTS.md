# AGENTS.md — Quy tắc làm việc cho AI / Developer

Tài liệu này **bắt buộc** đọc trước khi sửa hoặc tạo code trong repo `bank-system`.

## 1. Nguồn sự thật (SSOT)

| Loại quyết định | File |
|-----------------|------|
| Scope, không làm gì | `docs/00-overview/SCOPE_MVP.md` |
| Hardcode / mock / secret cần user cung cấp | `docs/00-overview/PROVIDE_LATER.md` |
| Demo 15′ / interview | `docs/DEMO_SCRIPT.md`, `docs/INTERVIEW_TALKING_POINTS.md` |
| Known limitations | `docs/KNOWN_LIMITATIONS.md` |
| Lộ trình phase | `docs/00-overview/ROADMAP.md` + `docs/08-phases/*` |
| Kiến trúc tổng | `docs/01-architecture/*` |
| Schema DB | `docs/02-data/*` |
| API contract | `docs/03-api/contracts/*` |
| Cách implement service | `docs/04-services/<service>/IMPLEMENT.md` |
| Frontend | `docs/05-frontend/*` |
| Infra | `docs/06-infra/*` |
| CI/CD | `docs/07-devops/*` |
| Quyết định đã chốt | `docs/99-decisions/*` |

**Nếu docs mâu thuẫn với “ý tưởng trong chat” → ưu tiên docs.**  
Muốn đổi plan → cập nhật docs + ADR trước, rồi mới code.

## 2. Workflow chuẩn mỗi task

```
1. Xác định phase (PHASE-0x-*.md)
2. Đọc OVERVIEW + contract liên quan
3. Đọc IMPLEMENT.md của module
4. Implement đúng checklist trong IMPLEMENT.md
5. Cập nhật checklist status trong phase file (nếu có)
6. Không tự thêm service/feature ngoài MVP
```

## 3. Cấm (anti-patterns)

- ❌ Thêm `card-service`, `config-server`, ELK full stack trong MVP
- ❌ Shared database giữa services
- ❌ Distributed transaction 2PC / XA
- ❌ Hardcode secret trong code (dùng env / compose)
- ❌ Bypass Gateway khi gọi từ frontend (FE chỉ gọi Gateway)
- ❌ Bỏ Saga/Outbox ở transfer “cho nhanh”
- ❌ Đổi stack (Quarkus, NestJS, React…) trừ khi có ADR mới

## 4. Convention bắt buộc

- Package: `com.banksystem.<service>`
- API public: `/api/v1/...` qua Gateway
- Response envelope: xem `docs/03-api/api-conventions.md`
- Error code: `DOMAIN_ERROR_CODE` (string constant)
- ID: UUID (string) trừ số tài khoản bank-style
- Timezone: UTC trong DB; ISO-8601 trong API
- Logs: structured (JSON nếu có thể), có `correlationId` / `traceId`

## 5. Prompt template khi user yêu cầu code

AI phải tự load (không hỏi lại nếu đã có trong docs):

```
Context: bank-system MVP
Phase: <phase-id>
Module: <service or frontend module>
Read:
  - docs/08-phases/<phase>.md
  - docs/04-services/<service>/IMPLEMENT.md  (hoặc 05-frontend)
  - docs/03-api/contracts/<service>.md
  - docs/02-data/er-diagrams/<service>.md
Do: implement exactly the checklist; no scope creep.
```

## 6. Definition of Done (mỗi service)

- [ ] Scaffold Spring Boot chạy local / Docker
- [ ] Entity + migration (Flyway)
- [ ] API theo contract
- [ ] Unit test core logic (≥ critical paths)
- [ ] Actuator health
- [ ] Swagger/OpenAPI
- [ ] Register Eureka (trừ gateway/discovery theo design)
- [ ] Log + basic metrics
- [ ] Không commit secrets

## 7. Ngôn ngữ docs

- Docs chính: **Tiếng Việt** (giải thích) + **English identifiers** (code, API path, topic Kafka)
- Code comments: English ngắn gọn
