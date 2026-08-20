-- V42: Virtual Account Pools and Provisioned Virtual Accounts

CREATE TABLE virtual_account_pools (
  id          UUID PRIMARY KEY,
  provider    VARCHAR(50) NOT NULL,
  bank_bin    VARCHAR(20) NOT NULL,
  prefix      VARCHAR(20) NOT NULL,
  start_seq   BIGINT NOT NULL,
  end_seq     BIGINT NOT NULL,
  current_seq BIGINT NOT NULL,
  status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE virtual_accounts (
  id                 UUID PRIMARY KEY,
  organization_id    UUID NOT NULL,
  provider           VARCHAR(50) NOT NULL,
  bank_bin           VARCHAR(20) NOT NULL,
  account_number     VARCHAR(50) NOT NULL,
  parent_account_id  UUID,
  mode               VARCHAR(30) NOT NULL, -- SINGLE_USE, FIXED_PAYER
  customer_reference VARCHAR(100),
  status             VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- RESERVED, ACTIVE, EXPIRED, CLOSED
  activated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at         TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_virtual_account UNIQUE (provider, bank_bin, account_number)
);

CREATE INDEX idx_va_org ON virtual_accounts(organization_id);
CREATE INDEX idx_va_lookup ON virtual_accounts(provider, bank_bin, account_number);
CREATE INDEX idx_va_status ON virtual_accounts(status);
CREATE INDEX idx_va_cust_ref ON virtual_accounts(customer_reference);

-- Seed initial default pools for MOCK and SEPAY providers
INSERT INTO virtual_account_pools (
  id, provider, bank_bin, prefix, start_seq, end_seq, current_seq, status, created_at, updated_at
) VALUES (
  'd0000000-0000-0000-0000-000000000001',
  'MOCK',
  '970422',
  '8888',
  100000,
  999999,
  100000,
  'ACTIVE',
  NOW(),
  NOW()
), (
  'd0000000-0000-0000-0000-000000000002',
  'SEPAY',
  '970422',
  '9999',
  100000,
  999999,
  100000,
  'ACTIVE',
  NOW(),
  NOW()
), (
  'd0000000-0000-0000-0000-000000000003',
  'NAPAS',
  '970436',
  '7777',
  100000,
  999999,
  100000,
  'ACTIVE',
  NOW(),
  NOW()
);
