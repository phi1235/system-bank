CREATE TABLE forensic_graph_cache (
  transaction_id UUID PRIMARY KEY REFERENCES transfer_orders(id),
  graph_version BIGINT NOT NULL,
  completeness VARCHAR(30) NOT NULL,
  source_watermark TIMESTAMPTZ NOT NULL,
  graph_json JSONB NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forensic_graph_cache_expiry ON forensic_graph_cache (expires_at);

CREATE TABLE forensic_temporal_snapshots (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(40) NOT NULL,
  aggregate_id UUID NOT NULL,
  snapshot_at TIMESTAMPTZ NOT NULL,
  last_sequence BIGINT NOT NULL,
  schema_version INTEGER NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  storage_uri VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_forensic_temporal_snapshot UNIQUE (aggregate_type, aggregate_id, snapshot_at),
  CONSTRAINT ck_forensic_temporal_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_forensic_temporal_snapshot_lookup
  ON forensic_temporal_snapshots (aggregate_type, aggregate_id, snapshot_at DESC);
