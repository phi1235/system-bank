-- ====================================================================
-- V20260821160000: Add display_name to virtual_accounts for branded VA / QR
-- ====================================================================

ALTER TABLE virtual_accounts
  ADD COLUMN IF NOT EXISTS display_name VARCHAR(100);

-- Update existing default VA if any
UPDATE virtual_accounts
SET display_name = 'TechMart Vietnam'
WHERE display_name IS NULL;
