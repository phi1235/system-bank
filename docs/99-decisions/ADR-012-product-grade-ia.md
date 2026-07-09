# ADR-012 — Product-grade IA (header/footer IB + RBAC BO)

## Status

Accepted

## Context

User yêu cầu: không demo sơ sài; customer không dùng left tab như admin; admin cần RBAC và nhiều module cho dự án dài hơi, không chỉ apply job.

## Decision

1. **Customer Internet Banking:** header + footer shell; primary nav mở rộng (Thẻ, Wealth, Support placeholder).  
2. **Admin Back Office:** module sidenav + RBAC roles/permissions matrix.  
3. Docs SSOT: `INFORMATION_ARCHITECTURE.md`, `RBAC.md`, `PORTALS.md`.  
4. Mockup HTML cập nhật theo quyết định này trước khi code Angular Phase 6.

## Consequences

- Effort FE cao hơn “3 màn admin” — đúng hướng product.  
- Backend auth: roadmap permissions (P6 mock, P7+ tables).  
- Scope MVP code vẫn cắt theo phase; **IA/nav không được xóa placeholder**.
