CREATE TABLE financial_events (
  event_id UUID PRIMARY KEY,
  aggregate_type VARCHAR(40) NOT NULL,
  aggregate_id UUID NOT NULL,
  sequence_no BIGINT NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  schema_version INTEGER NOT NULL,
  transaction_id UUID,
  occurred_at TIMESTAMPTZ NOT NULL,
  payload_json JSONB NOT NULL,
  payload_sha256 VARCHAR(64) NOT NULL,
  CONSTRAINT uq_financial_event_sequence UNIQUE (aggregate_type, aggregate_id, sequence_no),
  CONSTRAINT ck_financial_event_checksum CHECK (payload_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_financial_event_transaction
  ON financial_events (transaction_id, occurred_at ASC)
  WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_financial_event_aggregate
  ON financial_events (aggregate_type, aggregate_id, sequence_no ASC);

CREATE OR REPLACE FUNCTION reject_financial_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'financial_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_financial_event_no_mutation
BEFORE UPDATE OR DELETE ON financial_events
FOR EACH ROW EXECUTE FUNCTION reject_financial_event_mutation();
