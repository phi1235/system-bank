-- V43: Split Rules, Collection Orders, Inbound Payment Events, and Allocations

CREATE TABLE split_rules (
  id              UUID PRIMARY KEY,
  organization_id UUID NOT NULL,
  name            VARCHAR(100) NOT NULL,
  status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE split_rule_items (
  id               UUID PRIMARY KEY,
  split_rule_id    UUID NOT NULL REFERENCES split_rules(id) ON DELETE CASCADE,
  beneficiary_type VARCHAR(30) NOT NULL, -- PLATFORM, SELLER_INTERNAL, SELLER_EXTERNAL
  beneficiary_id   VARCHAR(100),
  account_id       UUID,
  bank_bin         VARCHAR(20),
  account_number   VARCHAR(50),
  beneficiary_name VARCHAR(255),
  split_type       VARCHAR(30) NOT NULL, -- PERCENTAGE, FIXED_AMOUNT, REMAINDER
  value            NUMERIC(19,4) NOT NULL,
  priority         INT NOT NULL DEFAULT 1,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE collection_orders (
  id                  UUID PRIMARY KEY,
  organization_id     UUID NOT NULL,
  merchant_order_id   VARCHAR(100) NOT NULL,
  virtual_account_id  UUID NOT NULL REFERENCES virtual_accounts(id),
  expected_amount     NUMERIC(19,2) NOT NULL,
  paid_amount         NUMERIC(19,2) NOT NULL DEFAULT 0,
  currency            VARCHAR(3) NOT NULL DEFAULT 'VND',
  status              VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, PARTIAL, PAID, OVERPAID, EXPIRED, CANCELLED, REVIEW
  customer_reference  VARCHAR(100),
  split_rule_snapshot TEXT,
  expires_at          TIMESTAMPTZ,
  paid_at             TIMESTAMPTZ,
  version             BIGINT NOT NULL DEFAULT 0,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_collection_merchant_order UNIQUE (organization_id, merchant_order_id)
);

CREATE TABLE inbound_payment_events (
  id                     UUID PRIMARY KEY,
  provider               VARCHAR(50) NOT NULL,
  provider_transaction_id VARCHAR(100) NOT NULL,
  virtual_account_number VARCHAR(50) NOT NULL,
  bank_bin               VARCHAR(20) NOT NULL,
  amount                 NUMERIC(19,2) NOT NULL,
  currency               VARCHAR(3) NOT NULL DEFAULT 'VND',
  sender_account         VARCHAR(50),
  sender_bank_bin        VARCHAR(20),
  sender_name            VARCHAR(255),
  reference_content      VARCHAR(500),
  raw_payload_hash       VARCHAR(64) NOT NULL,
  raw_payload            TEXT NOT NULL,
  status                 VARCHAR(30) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, MATCHED, PROCESSING, PENDING_RECOVERY, PROCESSED, UNMATCHED, MISMATCH, DUPLICATE, FAILED, DEAD_LETTER
  error_message          VARCHAR(500),
  retry_count            INT NOT NULL DEFAULT 0,
  next_retry_at          TIMESTAMPTZ,
  processed_at           TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_inbound_provider_tx UNIQUE (provider, provider_transaction_id)
);

CREATE TABLE payment_allocations (
  id                       UUID PRIMARY KEY,
  inbound_payment_event_id UUID NOT NULL REFERENCES inbound_payment_events(id),
  collection_order_id      UUID NOT NULL REFERENCES collection_orders(id),
  allocated_amount         NUMERIC(19,2) NOT NULL,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_payment_alloc_inbound UNIQUE (inbound_payment_event_id)
);

CREATE INDEX idx_split_rules_org ON split_rules(organization_id);
CREATE INDEX idx_split_items_rule ON split_rule_items(split_rule_id);
CREATE INDEX idx_collection_orders_org ON collection_orders(organization_id);
CREATE INDEX idx_collection_orders_va ON collection_orders(virtual_account_id);
CREATE INDEX idx_collection_orders_status ON collection_orders(status);
CREATE INDEX idx_inbound_va_num ON inbound_payment_events(virtual_account_number, bank_bin);
CREATE INDEX idx_inbound_status ON inbound_payment_events(status);
CREATE INDEX idx_payment_alloc_order ON payment_allocations(collection_order_id);
CREATE INDEX idx_payment_alloc_event ON payment_allocations(inbound_payment_event_id);
