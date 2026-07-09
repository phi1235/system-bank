# Contract — customer-service

Base: `/api/v1/customers`

## POST /me  (JWT CUSTOMER) — init profile

```json
{
  "fullName": "Nguyen Phi",
  "phone": "0901234567",
  "email": "phi@example.com",
  "nationalId": "001234567890",
  "address": "HCM"
}
```

201 if created; 409 if exists

## GET /me  (JWT)

```json
{
  "id": "uuid",
  "fullName": "...",
  "phone": "...",
  "email": "...",
  "nationalIdMasked": "*********7890",
  "kycStatus": "PENDING",
  "address": "..."
}
```

## PUT /me  (JWT)

Updatable: phone, address, fullName (not nationalId after set — or allow re-encrypt)

## Admin

### GET /  (ADMIN)

Query: page, size, q  
List customers (masked PII)

### PATCH /{id}/kyc  (ADMIN)

`{ "kycStatus": "VERIFIED" }`

## Internal

### GET /internal/customers/{id}/exists

`{ "exists": true }`  
Header: X-Internal-Api-Key
