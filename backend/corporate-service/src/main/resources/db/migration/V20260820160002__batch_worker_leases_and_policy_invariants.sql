ALTER TABLE payout_batches
  ADD COLUMN IF NOT EXISTS worker_claimed_by VARCHAR(100),
  ADD COLUMN IF NOT EXISTS worker_lease_until TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS hold_retry_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS hold_next_retry_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS hold_last_error VARCHAR(500);

ALTER TABLE payout_batches
  ADD CONSTRAINT ck_payout_batch_hold_retry_count CHECK (hold_retry_count >= 0);

CREATE INDEX IF NOT EXISTS idx_payout_batch_worker_claim
  ON payout_batches (status, hold_next_retry_at, worker_lease_until, updated_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_policy_one_active
  ON approval_policies (corporate_id)
  WHERE status = 'ACTIVE';
