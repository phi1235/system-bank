-- V44: Multi-tier Settlements, Settlement Legs, Payouts, and Settlement Audit Logs

CREATE TABLE settlements (
  id                  UUID PRIMARY KEY,
  organization_id     UUID NOT NULL,
  collection_order_id UUID NOT NULL UNIQUE REFERENCES collection_orders(id),
  gross_amount        NUMERIC(19,2) NOT NULL,
  platform_commission NUMERIC(19,2) NOT NULL DEFAULT 0,
  seller_net_amount   NUMERIC(19,2) NOT NULL,
  currency            VARCHAR(3) NOT NULL DEFAULT 'VND',
  status              VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, FUNDS_RESERVED, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, RETRYING, MANUAL_REVIEW, REVERSED
  ledger_journal_id   UUID,
  failure_reason      VARCHAR(500),
  version             BIGINT NOT NULL DEFAULT 0,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE settlement_legs (
  id               UUID PRIMARY KEY,
  settlement_id    UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
  beneficiary_type VARCHAR(30) NOT NULL, -- PLATFORM, SELLER_INTERNAL, SELLER_EXTERNAL
  beneficiary_id   VARCHAR(100),
  account_id       UUID,
  bank_bin         VARCHAR(20),
  account_number   VARCHAR(50),
  beneficiary_name VARCHAR(255),
  amount           NUMERIC(19,2) NOT NULL,
  currency         VARCHAR(3) NOT NULL DEFAULT 'VND',
  leg_type         VARCHAR(30) NOT NULL, -- INTERNAL_CREDIT, EXTERNAL_PAYOUT, COMMISSION
  status           VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED, RETRYING
  payout_id        UUID,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE b2b_payouts (
  id                         UUID PRIMARY KEY,
  organization_id            UUID NOT NULL,
  settlement_leg_id          UUID NOT NULL REFERENCES settlement_legs(id),
  payout_type                VARCHAR(30) NOT NULL DEFAULT 'NAPAS_247',
  amount                     NUMERIC(19,2) NOT NULL,
  currency                   VARCHAR(3) NOT NULL DEFAULT 'VND',
  beneficiary_account_id     UUID,
  beneficiary_bank_bin       VARCHAR(20) NOT NULL,
  beneficiary_account_number VARCHAR(50) NOT NULL,
  beneficiary_name           VARCHAR(255) NOT NULL,
  provider_reference         VARCHAR(100),
  status                     VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, SUCCESS, FAILED, REVERSED
  retry_count                INT NOT NULL DEFAULT 0,
  last_error                 VARCHAR(500),
  created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE settlement_audit_logs (
  id            UUID PRIMARY KEY,
  settlement_id UUID NOT NULL,
  action        VARCHAR(50) NOT NULL,
  actor_id      UUID,
  actor_role    VARCHAR(50),
  details       TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlements_org ON settlements(organization_id);
CREATE INDEX idx_settlements_status ON settlements(status);
CREATE INDEX idx_settlement_legs_settlement ON settlement_legs(settlement_id);
CREATE INDEX idx_b2b_payouts_org ON b2b_payouts(organization_id);
CREATE INDEX idx_b2b_payouts_status ON b2b_payouts(status);
CREATE INDEX idx_settlement_audit_settlement ON settlement_audit_logs(settlement_id);
