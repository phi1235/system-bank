# ADR-011 — Two separate portals (Customer IB vs Admin BO)

## Status

Accepted

## Context

Mockup ban đầu gộp admin + customer trong cùng mental model (gallery một list, dễ hiểu nhầm chung luồng). User feedback: **không ổn**.

## Decision

1. **Internet Banking** (`/auth`, `/customer`) — ROLE_CUSTOMER only  
2. **Back Office** (`/admin/login`, `/admin/*`) — ROLE_ADMIN only  
3. Shell, nav, visual theme, login entry **tách hẳn**  
4. Shared chỉ core API/token/pipes — không share layout UX  

## Consequences

- Angular: 2 layout modules + 2 auth feature areas  
- Role guard chặt: CUSTOMER không vào admin; ADMIN không vào customer shell  
- Mockup gallery: 2 cột portal  
- Docs: `PORTALS.md` là SSOT ranh giới UX  
