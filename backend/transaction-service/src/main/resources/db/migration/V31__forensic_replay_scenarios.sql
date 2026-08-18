CREATE TABLE forensic_replay_scenarios (
  scenario_id VARCHAR(100) PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  engine_key VARCHAR(80) NOT NULL,
  source_incident_id VARCHAR(100) NOT NULL,
  source_evidence_ref VARCHAR(200) NOT NULL,
  definition_json JSONB NOT NULL,
  sanitized BOOLEAN NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by UUID NOT NULL,
  confirmed_by UUID,
  confirmed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_forensic_scenario_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'DISABLED')),
  CONSTRAINT ck_forensic_scenario_confirmation CHECK (
    (status = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL)
    OR (status IN ('CONFIRMED', 'DISABLED') AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
  )
);

CREATE INDEX idx_forensic_scenario_status ON forensic_replay_scenarios (status, updated_at DESC);
