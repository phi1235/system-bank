CREATE TABLE forensic_verification_watermarks (
  job_name VARCHAR(80) PRIMARY KEY,
  watermark TIMESTAMPTZ NOT NULL,
  lease_owner UUID,
  lease_until TIMESTAMPTZ,
  last_error VARCHAR(500),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO forensic_verification_watermarks (job_name, watermark)
VALUES ('TRANSACTION_VERIFICATION', TIMESTAMPTZ '1970-01-01 00:00:00+00')
ON CONFLICT (job_name) DO NOTHING;

ALTER TABLE forensic_findings
  ADD COLUMN acknowledged_by UUID,
  ADD COLUMN acknowledged_at TIMESTAMPTZ,
  ADD COLUMN resolution_reason VARCHAR(1000),
  ADD COLUMN resolution_evidence JSONB,
  ADD COLUMN resolved_by UUID,
  ADD COLUMN resolved_at TIMESTAMPTZ;

CREATE INDEX idx_forensic_findings_queue
  ON forensic_findings (disposition, severity, last_seen_at DESC);
