-- V11: Create tables for Bill Payment Service (Electricity, Water, Internet, Mobile Top-up)

CREATE TABLE IF NOT EXISTS bill_categories (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon_url VARCHAR(255),
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS bill_providers (
    id VARCHAR(50) PRIMARY KEY,
    category_id VARCHAR(50) NOT NULL REFERENCES bill_categories(id),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS bill_payments (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    category_id VARCHAR(50) NOT NULL,
    provider_id VARCHAR(50) NOT NULL,
    customer_code VARCHAR(100) NOT NULL,
    customer_name VARCHAR(150),
    amount DECIMAL(18, 2) NOT NULL,
    fee DECIMAL(18, 2) DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    transaction_ref VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Categories
INSERT INTO bill_categories (id, name, icon_url, display_order, active) VALUES
('ELECTRICITY', 'Tiền điện', 'assets/images/bills/electricity.svg', 1, true),
('WATER', 'Tiền nước', 'assets/images/bills/water.svg', 2, true),
('INTERNET', 'Cước Internet', 'assets/images/bills/internet.svg', 3, true),
('MOBILE_TOPUP', 'Nạp tiền điện thoại', 'assets/images/bills/mobile.svg', 4, true)
ON CONFLICT (id) DO NOTHING;

-- Seed Providers
INSERT INTO bill_providers (id, category_id, name, code, active) VALUES
('EVN_HANOI', 'ELECTRICITY', 'EVN Hà Nội', 'EVNHN', true),
('EVN_HCM', 'ELECTRICITY', 'EVN TP.Hồ Chí Minh', 'EVNHCM', true),
('HAWACO', 'WATER', 'Nước sạch Hà Nội', 'HAWACO', true),
('SAWACO', 'WATER', 'Nước sạch Sài Gòn', 'SAWACO', true),
('VIETTEL_NET', 'INTERNET', 'Viettel Telecom', 'VTNET', true),
('FPT_TELECOM', 'INTERNET', 'FPT Telecom', 'FPTNET', true),
('VT_TOPUP', 'MOBILE_TOPUP', 'Viettel Mobile', 'VTTOPUP', true),
('VINA_TOPUP', 'MOBILE_TOPUP', 'VinaPhone', 'VINATOPUP', true)
ON CONFLICT (id) DO NOTHING;
