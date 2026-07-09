# API Conventions

## Base

- Public: `http://localhost:8080/api/v1`
- Content-Type: `application/json`
- Auth: `Authorization: Bearer <accessToken>`

## Response envelope

**Success:**

```json
{
  "success": true,
  "data": { },
  "meta": {
    "correlationId": "uuid",
    "timestamp": "2026-07-09T10:00:00Z"
  }
}
```

**Error:**

```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "Account balance is insufficient",
    "details": []
  },
  "meta": {
    "correlationId": "uuid",
    "timestamp": "2026-07-09T10:00:00Z"
  }
}
```

Common lib: `ApiResponse<T>`, `ApiError`, `BusinessException(code, message, httpStatus)`.

## HTTP status

| Case | Status |
|------|--------|
| OK | 200 |
| Created | 201 |
| Validation | 400 |
| Unauthorized | 401 |
| Forbidden | 403 |
| Not found | 404 |
| Conflict / idempotency body mismatch | 409 |
| Business rule (insufficient funds) | 422 |
| Rate limit | 429 |
| Server | 500 |

## Pagination

```
GET ...?page=0&size=20&sort=createdAt,desc

data: {
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

## Headers

| Header | Required | Notes |
|--------|----------|-------|
| Authorization | most | Bearer |
| Idempotency-Key | transfer | UUID string |
| X-Correlation-Id | optional | GW generates if missing |
| X-Internal-Api-Key | internal only | |

## Versioning

URL path `/api/v1` only for MVP. No breaking changes without new version.

## OpenAPI

- springdoc-openapi mỗi service
- Gateway có thể aggregate sau (optional)
- Tag theo controller domain
