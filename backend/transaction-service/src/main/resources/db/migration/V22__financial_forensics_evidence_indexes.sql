CREATE INDEX IF NOT EXISTS idx_transfer_forensics_risk_created
  ON transfer_orders (risk_decision, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_created
  ON outbox_events (aggregate_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_recon_items_transfer_kind
  ON recon_items (transfer_id, kind)
  WHERE transfer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_resource_created
  ON audit_logs (resource_id, created_at ASC)
  WHERE resource_id IS NOT NULL;
