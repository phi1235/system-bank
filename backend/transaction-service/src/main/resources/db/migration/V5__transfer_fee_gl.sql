-- Ledger entry id for fee credit on bank income account (GL).
ALTER TABLE transfer_orders
  ADD COLUMN fee_entry_ref VARCHAR(64);

COMMENT ON COLUMN transfer_orders.fee_entry_ref IS
  'Ledger entry id of fee CREDIT on bank income account; null when fee=0 or not posted.';
