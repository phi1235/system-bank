CREATE TABLE forensic_findings (
  id UUID PRIMARY KEY,
  finding_key VARCHAR(160) NOT NULL UNIQUE,
  case_id UUID REFERENCES forensic_cases(id),
  transaction_id UUID,
  rule_code VARCHAR(80) NOT NULL,
  subject_type VARCHAR(30) NOT NULL,
  subject_id VARCHAR(100) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  disposition VARCHAR(30) NOT NULL DEFAULT 'UNREVIEWED',
  title VARCHAR(200) NOT NULL,
  detail VARCHAR(2000),
  evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  evidence_hash VARCHAR(64) NOT NULL,
  occurrence_count INTEGER NOT NULL DEFAULT 1,
  detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  reviewed_by UUID,
  reviewed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_forensic_finding_transaction
  ON forensic_findings (transaction_id, severity, detected_at DESC)
  WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_forensic_finding_case
  ON forensic_findings (case_id, detected_at DESC)
  WHERE case_id IS NOT NULL;

CREATE TABLE forensic_evidence_references (
  id UUID PRIMARY KEY,
  case_id UUID REFERENCES forensic_cases(id),
  finding_id UUID REFERENCES forensic_findings(id),
  subject_type VARCHAR(30) NOT NULL,
  subject_id VARCHAR(100) NOT NULL,
  source VARCHAR(40) NOT NULL,
  source_reference_id VARCHAR(160) NOT NULL,
  schema_version INTEGER NOT NULL,
  checksum_sha256 VARCHAR(64) NOT NULL,
  sensitivity VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  storage_uri VARCHAR(500),
  content_type VARCHAR(100),
  size_bytes BIGINT,
  captured_at TIMESTAMPTZ NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_forensic_evidence_owner CHECK (case_id IS NOT NULL OR finding_id IS NOT NULL),
  CONSTRAINT ck_forensic_evidence_checksum CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT uq_forensic_evidence_source UNIQUE (source, source_reference_id, checksum_sha256)
);

CREATE INDEX idx_forensic_evidence_subject
  ON forensic_evidence_references (subject_type, subject_id, captured_at DESC);

CREATE INDEX idx_forensic_evidence_case
  ON forensic_evidence_references (case_id, captured_at DESC)
  WHERE case_id IS NOT NULL;
