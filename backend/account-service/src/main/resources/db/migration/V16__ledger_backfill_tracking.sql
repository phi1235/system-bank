ALTER TABLE ledger_backfill_checkpoints
  ADD COLUMN last_ledger_entry_created_at TIMESTAMPTZ,
  ADD COLUMN started_at TIMESTAMPTZ,
  ADD COLUMN completed_at TIMESTAMPTZ;

CREATE TABLE ledger_backfill_exceptions (
  id UUID PRIMARY KEY,
  job_name VARCHAR(80) NOT NULL REFERENCES ledger_backfill_checkpoints(job_name),
  ledger_entry_id UUID NOT NULL REFERENCES ledger_entries(id),
  error_code VARCHAR(80) NOT NULL,
  error_detail VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_ledger_backfill_exception UNIQUE (job_name, ledger_entry_id)
);

CREATE INDEX idx_ledger_backfill_exception_job
  ON ledger_backfill_exceptions (job_name, created_at);
