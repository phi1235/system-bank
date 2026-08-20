-- V41: Corporate payout batch and item metadata on transfer_orders

ALTER TABLE transfer_orders
  ADD COLUMN corporate_id UUID,
  ADD COLUMN batch_id UUID,
  ADD COLUMN batch_item_id UUID,
  ADD COLUMN hold_id UUID,
  ADD COLUMN initiated_by UUID,
  ADD COLUMN execution_version INT NOT NULL DEFAULT 1;

CREATE INDEX idx_transfer_orders_corporate ON transfer_orders(corporate_id, batch_id);
CREATE INDEX idx_transfer_orders_batch_item ON transfer_orders(batch_id, batch_item_id, execution_version);
