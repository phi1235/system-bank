-- Additive migration: existing rows remain valid internal transfers.
ALTER TABLE transfer_orders
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS transfer_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
  ADD COLUMN IF NOT EXISTS target_bank_code VARCHAR(20),
  ADD COLUMN IF NOT EXISTS target_account_name VARCHAR(160),
  ADD COLUMN IF NOT EXISTS provider_reference_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS provider_status VARCHAR(30),
  ADD COLUMN IF NOT EXISTS provider_attempt_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_provider_query_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_transfer_provider_ref
  ON transfer_orders(provider_reference_id)
  WHERE provider_reference_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transfer_unknown_provider
  ON transfer_orders(status, last_provider_query_at)
  WHERE status IN ('UNKNOWN', 'REVIEW_REQUIRED');
