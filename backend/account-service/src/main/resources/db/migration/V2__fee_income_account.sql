-- Bank fee income (internal) account for transfer fee GL posting.
-- Fixed ids so transaction-service can resolve by account number (config default 1099999999).
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
  '00000000-0000-0000-0000-0000000000fe',
  '00000000-0000-0000-0000-000000000001',
  '1099999999',
  'INTERNAL',
  'VND',
  0,
  'ACTIVE',
  NOW(),
  NOW()
)
ON CONFLICT (account_number) DO NOTHING;
