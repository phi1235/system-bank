-- V10: Add performance indexes for transfer_orders and beneficiaries tables

CREATE INDEX IF NOT EXISTS idx_transfer_from_account_created ON transfer_orders(from_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transfer_status_created ON transfer_orders(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_beneficiaries_user_id ON beneficiaries(user_id);
