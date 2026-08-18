CREATE TABLE forensic_verification_runs (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES transfer_orders(id),
  requested_by UUID NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  rule_set_version VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  outcome VARCHAR(20),
  source_watermark TIMESTAMPTZ,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  CONSTRAINT uq_forensic_verification_idempotency
    UNIQUE (requested_by, idempotency_key),
  CONSTRAINT ck_forensic_verification_status
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_forensic_verification_transaction
  ON forensic_verification_runs (transaction_id, started_at DESC);

CREATE TABLE forensic_verification_results (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES forensic_verification_runs(id) ON DELETE CASCADE,
  rule_code VARCHAR(80) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  message VARCHAR(500) NOT NULL,
  evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  evaluated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_forensic_verification_rule UNIQUE (run_id, rule_code)
);

CREATE INDEX idx_forensic_verification_result_run
  ON forensic_verification_results (run_id, severity, outcome);
