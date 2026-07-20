-- Persist computed transfer fee (principal stays in amount).
-- Fee is charged on source debit (amount + fee); destination receives principal only.
ALTER TABLE transfer_orders
  ADD COLUMN fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0
    CHECK (fee_amount >= 0);

COMMENT ON COLUMN transfer_orders.fee_amount IS
  'Fee charged to source in addition to amount; not credited to destination in this skeleton.';
