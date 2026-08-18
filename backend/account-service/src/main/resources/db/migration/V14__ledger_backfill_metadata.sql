CREATE TABLE ledger_backfill_checkpoints (
  job_name VARCHAR(80) PRIMARY KEY,
  last_ledger_entry_id UUID,
  processed_count BIGINT NOT NULL DEFAULT 0,
  exception_count BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO ledger_backfill_checkpoints (job_name)
VALUES ('LEGACY_LEDGER_TO_JOURNAL')
ON CONFLICT (job_name) DO NOTHING;
