ALTER TABLE receipt_artifacts
  ADD COLUMN IF NOT EXISTS email_recipient VARCHAR(160),
  ADD COLUMN IF NOT EXISTS email_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
  ADD COLUMN IF NOT EXISTS email_retry_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS email_next_attempt_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS email_claimed_by VARCHAR(100),
  ADD COLUMN IF NOT EXISTS email_lease_until TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS email_last_error VARCHAR(500);

ALTER TABLE receipt_artifacts
  ADD CONSTRAINT ck_receipt_email_status
  CHECK (email_status IN ('NOT_REQUIRED', 'PENDING', 'SENDING', 'QUEUED', 'DEAD_LETTER'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_receipt_artifact_item_type
  ON receipt_artifacts (item_id, artifact_type)
  WHERE item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_receipt_artifact_batch_report
  ON receipt_artifacts (batch_id, artifact_type)
  WHERE item_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_receipt_email_delivery
  ON receipt_artifacts (email_status, email_next_attempt_at, email_lease_until)
  WHERE email_status IN ('PENDING', 'SENDING');
