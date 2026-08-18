CREATE TABLE forensic_export_jobs (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES forensic_cases(id),
  requested_by UUID NOT NULL,
  reason VARCHAR(500) NOT NULL,
  sensitivity VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  storage_uri VARCHAR(500),
  package_sha256 VARCHAR(64),
  error_detail VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_forensic_export_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'EXPIRED')),
  CONSTRAINT ck_forensic_export_sensitivity CHECK (sensitivity IN ('INTERNAL', 'RESTRICTED'))
);

CREATE INDEX idx_forensic_export_case ON forensic_export_jobs (case_id, created_at DESC);
CREATE INDEX idx_forensic_export_expiry ON forensic_export_jobs (expires_at) WHERE status = 'COMPLETED';

CREATE TABLE forensic_twin_forks (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES transfer_orders(id),
  created_by UUID NOT NULL,
  status VARCHAR(20) NOT NULL,
  snapshot_uri VARCHAR(500) NOT NULL,
  snapshot_sha256 VARCHAR(64) NOT NULL,
  schema_version INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  CONSTRAINT ck_forensic_fork_status CHECK (status IN ('READY', 'DELETED', 'EXPIRED'))
);

CREATE INDEX idx_forensic_fork_owner_active
  ON forensic_twin_forks (created_by, expires_at)
  WHERE status = 'READY';

CREATE TABLE forensic_replay_runs (
  id UUID PRIMARY KEY,
  fork_id UUID NOT NULL REFERENCES forensic_twin_forks(id),
  requested_by UUID NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  scenario_id VARCHAR(100) NOT NULL,
  seed BIGINT NOT NULL,
  target_commit_sha VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  result_uri VARCHAR(500),
  result_sha256 VARCHAR(64),
  error_detail VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_forensic_replay_idempotency UNIQUE (requested_by, idempotency_key),
  CONSTRAINT ck_forensic_replay_status CHECK (status IN ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'ERROR', 'EXPIRED'))
);

CREATE INDEX idx_forensic_replay_fork ON forensic_replay_runs (fork_id, created_at DESC);
CREATE INDEX idx_forensic_replay_expiry ON forensic_replay_runs (expires_at);
