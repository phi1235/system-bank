-- V39: Bank Directory, Provider Capabilities, and Beneficiary Inquiry Records

CREATE TABLE IF NOT EXISTS bank_directory (
    id UUID PRIMARY KEY,
    bin VARCHAR(16) NOT NULL UNIQUE,
    code VARCHAR(32) NOT NULL,
    short_name VARCHAR(64) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(512),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    lookup_supported BOOLEAN NOT NULL DEFAULT FALSE,
    qr_transfer_supported BOOLEAN NOT NULL DEFAULT FALSE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    last_sync_status VARCHAR(20) DEFAULT 'SUCCESS',
    last_sync_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bank_dir_code ON bank_directory(code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bank_dir_code_lower ON bank_directory(LOWER(code));
CREATE INDEX IF NOT EXISTS idx_bank_dir_bin ON bank_directory(bin);
CREATE INDEX IF NOT EXISTS idx_bank_dir_active ON bank_directory(active);

CREATE TABLE IF NOT EXISTS provider_bank_capabilities (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    bank_bin VARCHAR(16) NOT NULL REFERENCES bank_directory(bin) ON DELETE CASCADE,
    inquiry_supported BOOLEAN NOT NULL DEFAULT FALSE,
    payout_supported BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(64) NOT NULL DEFAULT 'PARTNER_CONFIG',
    last_checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(provider, bank_bin)
);

CREATE INDEX IF NOT EXISTS idx_pbc_provider_bin ON provider_bank_capabilities(provider, bank_bin);

CREATE TABLE IF NOT EXISTS beneficiary_inquiry_records (
    id UUID PRIMARY KEY,
    inquiry_id VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    bank_bin VARCHAR(16) NOT NULL,
    account_number_encrypted VARCHAR(512) NOT NULL,
    account_number_hmac VARCHAR(128) NOT NULL,
    provider_account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL DEFAULT 'INTERBANK',
    status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
    provider VARCHAR(32) NOT NULL,
    key_version INT NOT NULL DEFAULT 1,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inquiry_id ON beneficiary_inquiry_records(inquiry_id);
CREATE INDEX IF NOT EXISTS idx_inquiry_user_id ON beneficiary_inquiry_records(user_id);
CREATE INDEX IF NOT EXISTS idx_inquiry_hmac ON beneficiary_inquiry_records(account_number_hmac);
CREATE INDEX IF NOT EXISTS idx_inquiry_expires ON beneficiary_inquiry_records(expires_at);

ALTER TABLE transfer_orders
    ADD COLUMN IF NOT EXISTS beneficiary_inquiry_id VARCHAR(64);

ALTER TABLE transfer_orders
    ALTER COLUMN target_bank_code TYPE VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transfer_beneficiary_inquiry
    ON transfer_orders(beneficiary_inquiry_id)
    WHERE beneficiary_inquiry_id IS NOT NULL;

ALTER TABLE transfer_orders
    ADD CONSTRAINT fk_transfer_beneficiary_inquiry
    FOREIGN KEY (beneficiary_inquiry_id)
    REFERENCES beneficiary_inquiry_records(inquiry_id);

-- Seed initial bootstrap bank directory
INSERT INTO bank_directory (id, bin, code, short_name, full_name, logo_url, active)
VALUES
    ('11111111-1111-1111-1111-111111111111', '970499', 'SYSTEM_BANK', 'SystemBank', 'Ngân hàng Nội bộ SystemBank', 'assets/banks/systembank.png', TRUE),
    ('22222222-2222-2222-2222-222222222222', '970405', 'AGRIBANK', 'Agribank', 'Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam', 'https://api.vietqr.io/img/VBA.png', TRUE),
    ('33333333-3333-3333-3333-333333333333', '970415', 'VIETINBANK', 'VietinBank', 'Ngân hàng TMCP Công thương Việt Nam', 'https://api.vietqr.io/img/CTG.png', TRUE),
    ('44444444-4444-4444-4444-444444444444', '970436', 'VIETCOMBANK', 'Vietcombank', 'Ngân hàng TMCP Ngoại thương Việt Nam', 'https://api.vietqr.io/img/VCB.png', TRUE),
    ('55555555-5555-5555-5555-555555555555', '970418', 'BIDV', 'BIDV', 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam', 'https://api.vietqr.io/img/BIDV.png', TRUE),
    ('66666666-6666-6666-6666-666666666666', '970407', 'TECHCOMBANK', 'Techcombank', 'Ngân hàng TMCP Kỹ thương Việt Nam', 'https://api.vietqr.io/img/TCB.png', TRUE),
    ('77777777-7777-7777-7777-777777777777', '970422', 'MBBANK', 'MBBank', 'Ngân hàng TMCP Quân đội', 'https://api.vietqr.io/img/MB.png', TRUE),
    ('88888888-8888-8888-8888-888888888888', '970432', 'VPBANK', 'VPBank', 'Ngân hàng TMCP Việt Nam Thịnh Vượng', 'https://api.vietqr.io/img/VPB.png', TRUE),
    ('99999999-9999-9999-9999-999999999999', '970416', 'ACB', 'ACB', 'Ngân hàng TMCP Á Châu', 'https://api.vietqr.io/img/ACB.png', TRUE),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '970437', 'HDBANK', 'HDBank', 'Ngân hàng TMCP Phát triển TP. Hồ Chí Minh', 'https://api.vietqr.io/img/HDB.png', TRUE),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '970403', 'SACOMBANK', 'Sacombank', 'Ngân hàng TMCP Sài Gòn Thương Tín', 'https://api.vietqr.io/img/STB.png', TRUE),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '970423', 'TPBANK', 'TPBank', 'Ngân hàng TMCP Tiên Phong', 'https://api.vietqr.io/img/TPB.png', TRUE)
ON CONFLICT (bin) DO NOTHING;

-- Bootstrap the known directory so a fresh deployment can perform lookup before
-- the first successful VietQR directory sync. Unknown/new banks remain fail-closed.
UPDATE bank_directory
SET lookup_supported = TRUE,
    qr_transfer_supported = TRUE
WHERE bin <> '970499';

-- VietQR directory metadata authorizes lookup only. It must never grant payout.
INSERT INTO provider_bank_capabilities
    (id, provider, bank_bin, inquiry_supported, payout_supported, status, source)
SELECT gen_random_uuid(), 'VIETQR', bin, lookup_supported, FALSE, 'ACTIVE', 'VIETQR_DIRECTORY'
FROM bank_directory
ON CONFLICT (provider, bank_bin) DO NOTHING;

-- Mock inquiry is test-only and has no payout authority.
INSERT INTO provider_bank_capabilities
    (id, provider, bank_bin, inquiry_supported, payout_supported, status, source)
SELECT gen_random_uuid(), 'MOCK', bin, lookup_supported, FALSE, 'ACTIVE', 'LOCAL_SIMULATOR'
FROM bank_directory
ON CONFLICT (provider, bank_bin) DO NOTHING;

-- Payout capability is independent from VietQR. The local simulator supports all
-- external bootstrap banks; real NAPAS inherits the existing partner catalogue.
INSERT INTO provider_bank_capabilities
    (id, provider, bank_bin, inquiry_supported, payout_supported, status, source)
SELECT gen_random_uuid(), 'MOCK_NAPAS', bin, FALSE, bin <> '970499', 'ACTIVE', 'LOCAL_SIMULATOR'
FROM bank_directory
ON CONFLICT (provider, bank_bin) DO NOTHING;

INSERT INTO provider_bank_capabilities
    (id, provider, bank_bin, inquiry_supported, payout_supported, status, source)
SELECT gen_random_uuid(), 'NAPAS', d.bin, FALSE,
       COALESCE(b.napas_supported, FALSE),
       CASE WHEN COALESCE(b.status, 'INACTIVE') = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
       'LEGACY_PARTNER_CONFIG'
FROM bank_directory d
LEFT JOIN banks b ON b.bin = d.bin
ON CONFLICT (provider, bank_bin) DO NOTHING;
