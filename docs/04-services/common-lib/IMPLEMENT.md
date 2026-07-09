# IMPLEMENT — common-lib

## Goal

Shared Maven/Gradle module (jar) used by all backend services. **Không** deploy container.

## Path

`backend/common-lib/`

## Deliverables

- [ ] `ApiResponse<T>`, `ApiError`, `Meta`
- [ ] `BusinessException` + `@ControllerAdvice` base `GlobalExceptionHandler` (copy or shared)
- [ ] Error code constants class (optional per domain)
- [ ] `CryptoUtils` AES-GCM encrypt/decrypt
- [ ] JWT claim names constants: `ROLE_CLAIM`, headers `X-User-Id`, `X-User-Roles`, `X-Correlation-Id`
- [ ] Page DTO `PageResponse<T>`
- [ ] Validation helpers if needed

## Rules

- Java 21
- No Spring Cloud dependency heavy — only spring-web / jackson / validation as needed
- Version aligned parent POM
- Do **not** put Feign clients here (circular coupling)

## Tests

- Unit test CryptoUtils roundtrip
- Unit test ApiResponse serialization smoke

## Depends on docs

- `docs/03-api/api-conventions.md`
- `docs/01-architecture/security.md` (AES)
