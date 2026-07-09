# ER — customer-service (`bank_customer`)

```mermaid
erDiagram
  customers {
    uuid id PK "same as userId"
    string full_name
    string phone
    string email
    string national_id_encrypted
    string kyc_status "PENDING|VERIFIED|REJECTED"
    string address
    timestamptz created_at
    timestamptz updated_at
  }
```

```sql
CREATE TABLE customers (
  id UUID PRIMARY KEY,
  full_name VARCHAR(200) NOT NULL,
  phone VARCHAR(20),
  email VARCHAR(255),
  national_id_encrypted TEXT,
  kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  address VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## API mask

- Response never returns raw nationalId; return `nationalIdMasked` e.g. `***********123`
- Update nationalId: encrypt before save
