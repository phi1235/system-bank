CREATE TABLE forensic_case_history (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES forensic_cases(id),
  actor_user_id UUID NOT NULL,
  action VARCHAR(50) NOT NULL,
  from_status VARCHAR(30),
  to_status VARCHAR(30) NOT NULL,
  decision VARCHAR(40),
  note VARCHAR(2000),
  case_version BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forensic_case_history_case
  ON forensic_case_history (case_id, created_at DESC);

CREATE OR REPLACE FUNCTION reject_forensic_history_mutation()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'forensic_case_history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_forensic_history_no_update
BEFORE UPDATE OR DELETE ON forensic_case_history
FOR EACH ROW EXECUTE FUNCTION reject_forensic_history_mutation();
