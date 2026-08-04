-- V16: Covering index for transaction report aggregate queries.
-- The summary/daily/status/top queries scan transfer_orders by created_at range
-- and aggregate status, amount, fee_amount, from_account_id.
-- This covering index enables Index-Only Scans, avoiding slow heap fetches on 1M+ rows.
CREATE INDEX IF NOT EXISTS idx_transfer_report_covering
  ON transfer_orders(created_at, status, from_account_id, amount, fee_amount);
