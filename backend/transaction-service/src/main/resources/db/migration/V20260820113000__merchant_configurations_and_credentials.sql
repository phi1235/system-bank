-- V41: Merchant B2B Configurations, API Credentials, and Webhook Endpoints

CREATE TABLE merchant_accounts (
  id                    UUID PRIMARY KEY,
  organization_id       UUID NOT NULL UNIQUE,
  collection_account_id UUID NOT NULL,
  escrow_account_id     UUID NOT NULL,
  commission_account_id UUID NOT NULL,
  default_currency      VARCHAR(3) NOT NULL DEFAULT 'VND',
  status                VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE merchant_api_credentials (
  id               UUID PRIMARY KEY,
  key_id           VARCHAR(50) NOT NULL UNIQUE,
  organization_id  UUID NOT NULL,
  name             VARCHAR(100) NOT NULL,
  secret_hash      VARCHAR(100) NOT NULL,
  encrypted_secret TEXT NOT NULL,
  status           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  expires_at       TIMESTAMPTZ,
  last_used_at     TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE merchant_webhook_endpoints (
  id               UUID PRIMARY KEY,
  organization_id  UUID NOT NULL,
  url              VARCHAR(500) NOT NULL,
  event_types      VARCHAR(255) NOT NULL DEFAULT 'collection.order.paid.v1',
  secret_hash      VARCHAR(100) NOT NULL,
  encrypted_secret TEXT NOT NULL,
  status           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchant_accounts_org ON merchant_accounts(organization_id);
CREATE INDEX idx_merchant_creds_org ON merchant_api_credentials(organization_id);
CREATE INDEX idx_merchant_webhooks_org ON merchant_webhook_endpoints(organization_id);

-- Seed default merchant config for demo organization TECHMART_VN
INSERT INTO merchant_accounts (
  id, organization_id, collection_account_id, escrow_account_id, commission_account_id, default_currency, status, created_at, updated_at
) VALUES (
  'c0000000-0000-0000-0000-000000000001',
  'a0000000-0000-0000-0000-000000000001',
  'b0000000-0000-0000-0000-000000000001',
  'b0000000-0000-0000-0000-000000000002',
  'b0000000-0000-0000-0000-000000000001',
  'VND',
  'ACTIVE',
  NOW(),
  NOW()
) ON CONFLICT (organization_id) DO NOTHING;
