CREATE TABLE IF NOT EXISTS banks (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    bin VARCHAR(16) NOT NULL,
    short_name VARCHAR(64) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(512),
    napas_supported BOOLEAN NOT NULL DEFAULT TRUE,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_banks_code ON banks(code);
CREATE INDEX IF NOT EXISTS idx_banks_status ON banks(status);

-- Seed Vietnamese Bank Directory Catalog
INSERT INTO banks (id, code, bin, short_name, full_name, logo_url, napas_supported, is_internal, status)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'SYSTEM_BANK', '970499', 'SystemBank', 'Ngân hàng Nội bộ SystemBank', 'assets/banks/systembank.png', TRUE, TRUE, 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222222', '970405', '970405', 'Agribank', 'Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam', 'https://img.vietqr.io/image/agribank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('33333333-3333-3333-3333-333333333333', '970415', '970415', 'VietinBank', 'Ngân hàng TMCP Công thương Việt Nam', 'https://img.vietqr.io/image/vietinbank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('44444444-4444-4444-4444-444444444444', '970436', '970436', 'Vietcombank', 'Ngân hàng TMCP Ngoại thương Việt Nam', 'https://img.vietqr.io/image/vietcombank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('55555555-5555-5555-5555-555555555555', '970418', '970418', 'BIDV', 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam', 'https://img.vietqr.io/image/bidv-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('66666666-6666-6666-6666-666666666666', '970407', '970407', 'Techcombank', 'Ngân hàng TMCP Kỹ thương Việt Nam', 'https://img.vietqr.io/image/techcombank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('77777777-7777-7777-7777-777777777777', '970422', '970422', 'MBBank', 'Ngân hàng TMCP Quân đội', 'https://img.vietqr.io/image/mbbank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('88888888-8888-8888-8888-888888888888', '970432', '970432', 'VPBank', 'Ngân hàng TMCP Việt Nam Thịnh Vượng', 'https://img.vietqr.io/image/vpbank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('99999999-9999-9999-9999-999999999999', '970416', '970416', 'ACB', 'Ngân hàng TMCP Á Châu', 'https://img.vietqr.io/image/acb-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '970437', '970437', 'HDBank', 'Ngân hàng TMCP Phát triển TP. Hồ Chí Minh', 'https://img.vietqr.io/image/hdbank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '970403', '970403', 'Sacombank', 'Ngân hàng TMCP Sài Gòn Thương Tín', 'https://img.vietqr.io/image/sacombank-logo.png', TRUE, FALSE, 'ACTIVE'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '970423', '970423', 'TPBank', 'Ngân hàng TMCP Tiên Phong', 'https://img.vietqr.io/image/tpbank-logo.png', TRUE, FALSE, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;
