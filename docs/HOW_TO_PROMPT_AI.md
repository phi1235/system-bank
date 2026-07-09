# Cách prompt AI khi code (không mất đồng bộ plan)

## Nguyên tắc

1. **Không** paste lại toàn bộ architecture mỗi lần.
2. Chỉ nêu **Phase + Module**.
3. AI **bắt buộc** đọc file trong repo theo `AGENTS.md`.
4. Mọi thay đổi scope → sửa docs/ADR **trước**, code **sau**.

---

## Template chuẩn

```
Làm đúng Phase <N> của bank-system.

Bắt buộc đọc:
- AGENTS.md
- docs/08-phases/PHASE-0X-....md
- docs/04-services/<service>/IMPLEMENT.md   # hoặc docs/05-frontend/...
- docs/03-api/contracts/<service>.md
- docs/02-data/er-diagrams/<service>.md

Yêu cầu:
- Chỉ implement checklist phase/module
- Không thêm service/feature ngoài SCOPE_MVP.md
- Xong thì liệt kê file đã tạo + checklist đã tick
```

---

## Ví dụ theo phase

### Phase 1
```
Implement PHASE-01 bank-system. Đọc docs/08-phases/PHASE-01-infra-skeleton.md.
Tạo Maven parent, common-lib, discovery-server, api-gateway stub, infra/docker-compose.
Không code business service.
```

### Phase 4 (saga)
```
Implement PHASE-04 transaction-service.
Primary: docs/01-architecture/saga-transfer.md
+ docs/04-services/transaction-service/IMPLEMENT.md
Outbox bắt buộc. Có flag fail-credit để demo compensate.
```

### Chỉ fix bug
```
Bug: transfer compensate không hoàn tiền.
Đọc saga-transfer.md + account debit/credit contract.
Chỉ sửa transaction-service + account-service liên quan. Không refactor FE.
```

### Đổi quyết định
```
Muốn đổi ADR-008 initial balance = 0.
Cập nhật ADR + contract + IMPLEMENT account trước, rồi mới sửa code.
```

---

## Checklist trước khi gửi prompt

- [ ] Đã xác định phase?
- [ ] File IMPLEMENT tồn tại?
- [ ] Không yêu cầu card-service / ELK / K8s?
- [ ] Nếu mâu thuẫn plan cũ → update docs trước?

---

## Map “muốn làm X → đọc file Y”

| Muốn làm | Đọc |
|----------|-----|
| Tổng quan | README + PROJECT_BRIEF + ROADMAP |
| Ranh giới service | architecture.md + SCOPE_MVP |
| Chuyển khoản | saga-transfer.md + transaction IMPLEMENT |
| Bảo mật | security.md + auth IMPLEMENT |
| API path/body | docs/03-api/contracts/* |
| Schema | docs/02-data/er-diagrams/* |
| Docker | docs/06-infra/docker-compose.md |
| FE màn hình | docs/05-frontend/* |
| Thứ tự code | docs/08-phases/* |
| Vì sao quyết định | docs/99-decisions/* |
