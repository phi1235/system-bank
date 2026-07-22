CREATE TABLE IF NOT EXISTS external_bank_accounts (
    id UUID PRIMARY KEY,
    bank_code VARCHAR(32) NOT NULL,
    account_number VARCHAR(32) NOT NULL,
    account_holder_name VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_bank_account UNIQUE (bank_code, account_number)
);

CREATE INDEX IF NOT EXISTS idx_ext_acc_num ON external_bank_accounts(bank_code, account_number);

-- Seed interbank test accounts for NAPAS account inquiry
INSERT INTO external_bank_accounts (id, bank_code, account_number, account_holder_name, status)
VALUES
    ('a1111111-1111-1111-1111-111111111111', '970415', '10987654321', 'NGUYEN VAN AN', 'ACTIVE'),
    ('a2222222-2222-2222-2222-222222222222', '970415', '10987654322', 'TRAN THI BINH', 'ACTIVE'),
    ('a3333333-3333-3333-3333-333333333333', '970405', '20987654321', 'LE HOANG CUONG', 'ACTIVE'),
    ('a4444444-4444-4444-4444-444444444444', '970436', '001100223344', 'PHAM MINH DUC', 'ACTIVE'),
    ('a5555555-5555-5555-5555-555555555555', '970418', '1234567890', 'VO THI HOANG YEN', 'ACTIVE'),
    ('a6666666-6666-6666-6666-666666666666', '970407', '190345678901', 'DANG QUOC BAO', 'ACTIVE')
ON CONFLICT (bank_code, account_number) DO NOTHING;
