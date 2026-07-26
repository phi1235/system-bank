-- End-of-day reconciliation: transfer_orders vs account-service ledger entries
-- (pulled over the internal API; databases are isolated per service, so no cross-DB join).
-- One run per execution; re-running the same business date creates a new run.

CREATE TABLE recon_runs (
  id UUID PRIMARY KEY,
  business_date DATE NOT NULL,
  zone VARCHAR(40) NOT NULL,
  trigger_type VARCHAR(20) NOT NULL,        -- SCHEDULED | MANUAL
  status VARCHAR(20) NOT NULL,              -- RUNNING | MATCHED | MISMATCHED | FAILED
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ,
  orders_checked INT NOT NULL DEFAULT 0,
  ledger_entries_seen INT NOT NULL DEFAULT 0,
  discrepancy_count INT NOT NULL DEFAULT 0,
  error_detail VARCHAR(500)
);
CREATE INDEX idx_recon_runs_date ON recon_runs(business_date, started_at DESC);

CREATE TABLE recon_items (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES recon_runs(id) ON DELETE CASCADE,
  transfer_id UUID,
  kind VARCHAR(40) NOT NULL,                -- MISSING_DEBIT, AMOUNT_MISMATCH_CREDIT, ...
  entry_ref VARCHAR(80),
  expected_amount NUMERIC(19,2),
  actual_amount NUMERIC(19,2),
  detail VARCHAR(255)
);
CREATE INDEX idx_recon_items_run ON recon_items(run_id);
