-- V21: Seed default B2B platform commission and escrow ledger accounts

INSERT INTO accounts (
  id,
  user_id,
  owner_type,
  owner_id,
  account_number,
  account_type,
  currency,
  balance,
  status,
  created_at,
  updated_at
) VALUES (
  'b0000000-0000-0000-0000-000000000001',
  '${system-user-id}',
  'INDIVIDUAL',
  '${system-user-id}',
  '999900000001',
  'INTERNAL',
  'VND',
  0,
  'ACTIVE',
  NOW(),
  NOW()
) ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (
  id,
  user_id,
  owner_type,
  owner_id,
  account_number,
  account_type,
  currency,
  balance,
  status,
  created_at,
  updated_at
) VALUES (
  'b0000000-0000-0000-0000-000000000002',
  '${system-user-id}',
  'INDIVIDUAL',
  '${system-user-id}',
  '999900000002',
  'INTERNAL',
  'VND',
  0,
  'ACTIVE',
  NOW(),
  NOW()
) ON CONFLICT (account_number) DO NOTHING;
