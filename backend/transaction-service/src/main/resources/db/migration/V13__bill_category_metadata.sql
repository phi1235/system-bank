-- V13: Add icon, sample_code, theme_class metadata columns to bill_categories table

ALTER TABLE bill_categories ADD COLUMN IF NOT EXISTS icon VARCHAR(50) DEFAULT 'receipt';
ALTER TABLE bill_categories ADD COLUMN IF NOT EXISTS sample_code VARCHAR(100);
ALTER TABLE bill_categories ADD COLUMN IF NOT EXISTS theme_class VARCHAR(50) DEFAULT 'cat-default';

UPDATE bill_categories SET icon = 'bolt', sample_code = 'PE0100123456', theme_class = 'cat-electricity' WHERE id = 'ELECTRICITY';
UPDATE bill_categories SET icon = 'water_drop', sample_code = 'WA0200654321', theme_class = 'cat-water' WHERE id = 'WATER';
UPDATE bill_categories SET icon = 'wifi', sample_code = 'IN0300987654', theme_class = 'cat-internet' WHERE id = 'INTERNET';
UPDATE bill_categories SET icon = 'phone_android', sample_code = '0988123456', theme_class = 'cat-mobile' WHERE id = 'MOBILE_TOPUP';
