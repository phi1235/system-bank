-- V14: Add performance composite & partial indexes for transfer_orders reporting and filter queries

CREATE INDEX IF NOT EXISTS idx_transfer_completed_summary
  ON transfer_orders (created_at, amount, fee_amount)
  WHERE status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_transfer_from_status_created
  ON transfer_orders (from_account_id, status, created_at DESC);
