# IMPLEMENT — customer-service

## Goal

Customer profile + KYC status + AES nationalId.

## Path

`backend/customer-service/`

## Read first

- contracts/customer-service.md
- er-diagrams/customer-service.md
- security.md (AES)

## Checklist

- [ ] Flyway customers table
- [ ] POST/GET/PUT /api/v1/customers/me
- [ ] Resolve userId from `X-User-Id` header (gateway)
- [ ] AES encrypt nationalId; mask in response
- [ ] Admin list + patch KYC
- [ ] Internal exists endpoint + API key
- [ ] Swagger, Actuator, Eureka
- [ ] Tests: encrypt mask, ownership

## Rules

- customer.id = userId (ADR-004)
- No password fields here
