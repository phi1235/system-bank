-- V20260820113005: Settlement, Payout, Inbound and Webhook State Hardening & Claim Fields

-- 1. Settlements Table Hardening
ALTER TABLE settlements ADD COLUMN IF NOT EXISTS command_id VARCHAR(100);
ALTER TABLE settlements ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE settlements ADD COLUMN IF NOT EXISTS overpaid_amount NUMERIC(19,2) NOT NULL DEFAULT 0;

-- Backfill command_id and request_hash if null
UPDATE settlements SET command_id = 'SETTLEMENT:' || id WHERE command_id IS NULL;
UPDATE settlements SET request_hash = 'LEGACY:' || MD5(id::text) WHERE request_hash IS NULL;
UPDATE settlements s
SET overpaid_amount = GREATEST(o.paid_amount - o.expected_amount, 0)
FROM collection_orders o
WHERE s.collection_order_id = o.id;
ALTER TABLE settlements ALTER COLUMN command_id SET NOT NULL;
ALTER TABLE settlements ALTER COLUMN request_hash SET NOT NULL;

-- Migrate legacy statuses
UPDATE settlements SET status = 'LEDGER_POSTED' WHERE status = 'FUNDS_RESERVED';
UPDATE settlements SET status = 'MANUAL_REVIEW' WHERE status IN ('PARTIALLY_COMPLETED', 'RETRYING');
UPDATE settlements SET status = 'LEDGER_PENDING' WHERE status = 'PENDING';
UPDATE settlements SET status = 'MANUAL_REVIEW', failure_reason = 'Legacy overpayment requires accounting review'
WHERE overpaid_amount > 0;

-- Add constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_settlements_command_id') THEN
    ALTER TABLE settlements ADD CONSTRAINT uq_settlements_command_id UNIQUE (command_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_settlements_collection_order') THEN
    ALTER TABLE settlements ADD CONSTRAINT uq_settlements_collection_order UNIQUE (collection_order_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_settlements_status') THEN
    ALTER TABLE settlements ADD CONSTRAINT chk_settlements_status CHECK (
      status IN ('PREPARING', 'LEDGER_PENDING', 'LEDGER_POSTED', 'PAYOUT_PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW', 'REVERSED')
    );
  END IF;
END $$;

-- 2. Settlement Legs Hardening
ALTER TABLE settlement_legs ADD COLUMN IF NOT EXISTS leg_key VARCHAR(100);
UPDATE settlement_legs SET leg_key = 'LEG:' || settlement_id || ':' || id WHERE leg_key IS NULL;
ALTER TABLE settlement_legs ALTER COLUMN leg_key SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_settlement_leg_key') THEN
    ALTER TABLE settlement_legs ADD CONSTRAINT uq_settlement_leg_key UNIQUE (settlement_id, leg_key);
  END IF;
END $$;

-- 3. B2B Payouts Hardening
ALTER TABLE b2b_payouts ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(100);
ALTER TABLE b2b_payouts ADD COLUMN IF NOT EXISTS clearing_journal_id UUID;
ALTER TABLE b2b_payouts ADD COLUMN IF NOT EXISTS claim_token UUID;
ALTER TABLE b2b_payouts ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;
ALTER TABLE b2b_payouts ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMPTZ;
ALTER TABLE b2b_payouts ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

-- Backfill client_request_id
UPDATE b2b_payouts SET client_request_id = 'NAPAS_PAYOUT:' || id WHERE client_request_id IS NULL;
ALTER TABLE b2b_payouts ALTER COLUMN client_request_id SET NOT NULL;

-- Migrate legacy statuses
UPDATE b2b_payouts SET status = 'READY' WHERE status = 'PENDING';
UPDATE b2b_payouts SET status = 'PENDING_RECON' WHERE status IN ('PROCESSING', 'RETRYING');
UPDATE b2b_payouts SET status = 'MANUAL_REVIEW' WHERE status = 'REVERSED';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payout_client_request_id') THEN
    ALTER TABLE b2b_payouts ADD CONSTRAINT uq_payout_client_request_id UNIQUE (client_request_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payout_settlement_leg') THEN
    ALTER TABLE b2b_payouts ADD CONSTRAINT uq_payout_settlement_leg UNIQUE (settlement_leg_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_b2b_payouts_status') THEN
    ALTER TABLE b2b_payouts ADD CONSTRAINT chk_b2b_payouts_status CHECK (
      status IN ('READY', 'DISPATCHING', 'PENDING_RECON', 'SWITCH_SUCCESS_LEDGER_PENDING', 'SUCCESS', 'FAILED', 'DEAD_LETTER', 'MANUAL_REVIEW')
    );
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_b2b_payouts_claim_retry ON b2b_payouts (status, next_retry_at, claim_expires_at);

-- 4. Inbound Payment Events Hardening
ALTER TABLE inbound_payment_events ADD COLUMN IF NOT EXISTS claim_token UUID;
ALTER TABLE inbound_payment_events ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;
ALTER TABLE inbound_payment_events ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMPTZ;
ALTER TABLE inbound_payment_events ADD COLUMN IF NOT EXISTS ledger_journal_id UUID;

-- Migrate legacy statuses
UPDATE inbound_payment_events SET status = 'LEDGER_PENDING' WHERE status = 'MATCHED';
UPDATE inbound_payment_events SET status = 'PENDING_RECOVERY' WHERE status = 'PROCESSING';
UPDATE inbound_payment_events SET status = 'PROCESSED' WHERE status = 'DUPLICATE';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inbound_status') THEN
    ALTER TABLE inbound_payment_events ADD CONSTRAINT chk_inbound_status CHECK (
      status IN ('RECEIVED', 'UNMATCHED', 'MISMATCH', 'LEDGER_PENDING', 'LEDGER_POSTED', 'FINALIZE_PENDING', 'PROCESSED', 'PENDING_RECOVERY', 'FAILED', 'DEAD_LETTER')
    );
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inbound_events_claim_retry ON inbound_payment_events (status, next_retry_at, claim_expires_at);

-- 5. Merchant Webhook Deliveries Hardening
ALTER TABLE merchant_webhook_deliveries ADD COLUMN IF NOT EXISTS claim_token UUID;
ALTER TABLE merchant_webhook_deliveries ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;
ALTER TABLE merchant_webhook_deliveries ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMPTZ;

UPDATE merchant_webhook_deliveries SET status = 'RETRYING' WHERE status = 'FAILED';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_merchant_webhook_deliveries_status') THEN
    ALTER TABLE merchant_webhook_deliveries ADD CONSTRAINT chk_merchant_webhook_deliveries_status CHECK (
      status IN ('PENDING', 'SENDING', 'SUCCESS', 'RETRYING', 'DEAD_LETTER')
    );
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_merchant_deliveries_claim_retry ON merchant_webhook_deliveries (status, next_retry_at, claim_expires_at);
