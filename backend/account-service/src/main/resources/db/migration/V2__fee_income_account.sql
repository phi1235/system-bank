-- Bank fee income (internal) account for transfer fee GL posting.
-- IDs provided via Flyway placeholders (see account-service application.yml + infra/.env).
-- This avoids hardcoding UUIDs in public source while keeping bootstrap deterministic per environment.
INSERT INTO accounts (
  id,
  user_id,
  account_number,
  account_type,
  currency,
  balance,
  status,
  created_at,
  updated_at
) VALUES (
  '${fee-income-id}',
  '${system-user-id}',
  '${fee-income-number}',
  'INTERNAL',
  'VND',
  0,
  'ACTIVE',
  NOW(),
  NOW()
)
ON CONFLICT (account_number) DO NOTHING;
