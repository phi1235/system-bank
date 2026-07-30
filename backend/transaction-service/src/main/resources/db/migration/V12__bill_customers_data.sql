-- V12: Bill Customers - sample lookup data for bill inquiry
-- In production, this would be replaced by external API calls to EVN, HAWACO, etc.

CREATE TABLE IF NOT EXISTS bill_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id VARCHAR(50) NOT NULL REFERENCES bill_providers(id),
    customer_code VARCHAR(100) NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    address VARCHAR(255),
    amount DECIMAL(18, 2) NOT NULL,
    period VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'UNPAID',
    UNIQUE(provider_id, customer_code)
);

-- Seed sample bill customers for demo
INSERT INTO bill_customers (provider_id, customer_code, customer_name, address, amount, period, status) VALUES
-- Electricity
('EVN_HANOI',   'PE0100123456', 'NGUYEN VAN AN',        '12 Tran Hung Dao, Hoan Kiem, Ha Noi',     450000,  'Tháng 07/2026', 'UNPAID'),
('EVN_HANOI',   'PE0100789012', 'TRAN THI BICH',        '45 Le Thanh Tong, Ba Dinh, Ha Noi',       380000,  'Tháng 07/2026', 'UNPAID'),
('EVN_HCM',     'PE0200345678', 'LE QUANG MINH',        '88 Nguyen Hue, Quan 1, TP.HCM',           520000,  'Tháng 07/2026', 'UNPAID'),
('EVN_HCM',     'PE0200111222', 'PHAM THI HONG',        '120 Vo Van Tan, Quan 3, TP.HCM',          295000,  'Tháng 07/2026', 'UNPAID'),
-- Water
('HAWACO',      'HN001234',     'NGUYEN VAN AN',        '12 Tran Hung Dao, Hoan Kiem, Ha Noi',     185000,  'Tháng 07/2026', 'UNPAID'),
('HAWACO',      'HN005678',     'DO MINH TUAN',         '78 Doi Can, Ba Dinh, Ha Noi',             142000,  'Tháng 07/2026', 'UNPAID'),
('SAWACO',      'HCM009012',    'LE QUANG MINH',        '88 Nguyen Hue, Quan 1, TP.HCM',           198000,  'Tháng 07/2026', 'UNPAID'),
-- Internet
('VIETTEL_NET', 'VT88001234',   'NGUYEN VAN AN',        '12 Tran Hung Dao, Hoan Kiem, Ha Noi',     220000,  'Tháng 07/2026', 'UNPAID'),
('VIETTEL_NET', 'VT88005678',   'TRAN QUOC VIET',       '33 Kim Ma, Ba Dinh, Ha Noi',              350000,  'Tháng 07/2026', 'UNPAID'),
('FPT_TELECOM', 'FPT10001234',  'PHAM THI HONG',        '120 Vo Van Tan, Quan 3, TP.HCM',          250000,  'Tháng 07/2026', 'UNPAID'),
-- Mobile Top-up (pre-defined denominations)
('VT_TOPUP',    '0901234567',   'NGUYEN VAN AN',        NULL,                                      100000,  'Nạp tiền',      'UNPAID'),
('VT_TOPUP',    '0912345678',   'TRAN THI BICH',        NULL,                                      200000,  'Nạp tiền',      'UNPAID'),
('VINA_TOPUP',  '0881234567',   'LE QUANG MINH',        NULL,                                      50000,   'Nạp tiền',      'UNPAID')
ON CONFLICT (provider_id, customer_code) DO NOTHING;
