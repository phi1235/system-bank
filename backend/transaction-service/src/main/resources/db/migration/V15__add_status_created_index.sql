-- V15: System-wide performance indexes for transaction service tables
CREATE INDEX IF NOT EXISTS idx_transfer_status_created
  ON transfer_orders (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_to_account_number
  ON transfer_orders (to_account_number);

CREATE INDEX IF NOT EXISTS idx_transfer_created_status_amount
  ON transfer_orders (created_at, status, amount, fee_amount, from_account_id);

CREATE INDEX IF NOT EXISTS idx_transfer_created_id
  ON transfer_orders (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created
  ON audit_logs (actor_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created
  ON audit_logs (action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_resource_type_created
  ON audit_logs (resource_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created
  ON audit_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
  ON outbox_events (status, created_at DESC);
