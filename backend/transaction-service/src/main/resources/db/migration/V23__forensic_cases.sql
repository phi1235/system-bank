CREATE TABLE forensic_cases (
  id UUID PRIMARY KEY,
  case_number VARCHAR(32) NOT NULL UNIQUE,
  transaction_id UUID,
  account_id UUID,
  source_type VARCHAR(30) NOT NULL,
  source_reference_id VARCHAR(100),
  status VARCHAR(30) NOT NULL,
  priority VARCHAR(20) NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(2000),
  evidence_completeness VARCHAR(20) NOT NULL DEFAULT 'EMPTY',
  assigned_to UUID,
  created_by UUID NOT NULL,
  submitted_by UUID,
  checker_id UUID,
  resolution_code VARCHAR(30),
  resolution_note VARCHAR(2000),
  submitted_at TIMESTAMPTZ,
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_forensic_case_subject CHECK (transaction_id IS NOT NULL OR account_id IS NOT NULL),
  CONSTRAINT ck_forensic_case_status CHECK (status IN (
    'OPEN', 'ASSIGNED', 'INVESTIGATING', 'PENDING_CHECKER',
    'RESOLVED', 'DISMISSED', 'DUPLICATE', 'REOPENED')),
  CONSTRAINT ck_forensic_case_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT ck_forensic_case_checker CHECK (
    checker_id IS NULL OR (checker_id <> created_by AND (submitted_by IS NULL OR checker_id <> submitted_by)))
);

CREATE UNIQUE INDEX uq_forensic_case_source_reference
  ON forensic_cases (source_type, source_reference_id)
  WHERE source_reference_id IS NOT NULL;

CREATE INDEX idx_forensic_case_queue
  ON forensic_cases (status, priority, created_at DESC);

CREATE INDEX idx_forensic_case_assignee
  ON forensic_cases (assigned_to, status, updated_at DESC);

CREATE INDEX idx_forensic_case_transaction
  ON forensic_cases (transaction_id, created_at DESC)
  WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_forensic_case_open
  ON forensic_cases (priority, updated_at DESC)
  WHERE status IN ('OPEN', 'ASSIGNED', 'INVESTIGATING', 'PENDING_CHECKER', 'REOPENED');
