-- Flyway V8: Seed 2-3 test accounts for each external bank in NAPAS network
INSERT INTO external_bank_accounts (id, bank_code, account_number, account_holder_name, status)
VALUES
    -- Agribank (970405)
    (gen_random_uuid(), '970405', '1500205123456', 'NGUYEN VAN HOANG', 'ACTIVE'),
    (gen_random_uuid(), '970405', '1500205987654', 'PHAN THI KIM ANH', 'ACTIVE'),
    (gen_random_uuid(), '970405', '1500205333444', 'HOANG VAN MINH', 'ACTIVE'),

    -- VietinBank (970415)
    (gen_random_uuid(), '970415', '10987654321', 'NGUYEN VAN AN', 'ACTIVE'),
    (gen_random_uuid(), '970415', '10987654322', 'TRAN THI BINH', 'ACTIVE'),
    (gen_random_uuid(), '970415', '10888999000', 'LE VAN THANG', 'ACTIVE'),

    -- Vietcombank (970436)
    (gen_random_uuid(), '970436', '001100223344', 'PHAM MINH DUC', 'ACTIVE'),
    (gen_random_uuid(), '970436', '007100123456', 'NGO THI DUNG', 'ACTIVE'),
    (gen_random_uuid(), '970436', '099100888999', 'VU HOANG NAM', 'ACTIVE'),

    -- BIDV (970418)
    (gen_random_uuid(), '970418', '1234567890', 'VO THI HOANG YEN', 'ACTIVE'),
    (gen_random_uuid(), '970418', '601100012345', 'TRINH VAN LAM', 'ACTIVE'),
    (gen_random_uuid(), '970418', '601100099988', 'DO THI NHUNG', 'ACTIVE'),

    -- Techcombank (970407)
    (gen_random_uuid(), '970407', '190345678901', 'DANG QUOC BAO', 'ACTIVE'),
    (gen_random_uuid(), '970407', '190399988877', 'BUI MINH TUAN', 'ACTIVE'),
    (gen_random_uuid(), '970407', '190311122233', 'TRAN HOANG ANH', 'ACTIVE'),

    -- MBBank (970422)
    (gen_random_uuid(), '970422', '0988888888', 'NGUYEN MANH HUNG', 'ACTIVE'),
    (gen_random_uuid(), '970422', '0333399999', 'HOANG THI NGAN', 'ACTIVE'),
    (gen_random_uuid(), '970422', '88889999111', 'LE PHUC HINH', 'ACTIVE'),

    -- VPBank (970432)
    (gen_random_uuid(), '970432', '123987654321', 'DINH VAN LONG', 'ACTIVE'),
    (gen_random_uuid(), '970432', '999888777666', 'TRAN THI HA', 'ACTIVE'),

    -- ACB (970416)
    (gen_random_uuid(), '970416', '888999111', 'TRUONG DUC THANH', 'ACTIVE'),
    (gen_random_uuid(), '970416', '777666555', 'VO VAN KIET', 'ACTIVE'),

    -- HDBank (970437)
    (gen_random_uuid(), '970437', '068704370001', 'NGUYEN THI CAM TU', 'ACTIVE'),
    (gen_random_uuid(), '970437', '068704370002', 'LE MINH TRI', 'ACTIVE'),

    -- Sacombank (970403)
    (gen_random_uuid(), '970403', '060012345678', 'DUONG THANH SON', 'ACTIVE'),
    (gen_random_uuid(), '970403', '060098765432', 'PHAN THI BICH', 'ACTIVE'),

    -- TPBank (970423)
    (gen_random_uuid(), '970423', '00011223344', 'NGUYEN DUC PHONG', 'ACTIVE'),
    (gen_random_uuid(), '970423', '00055667788', 'TRAN THANH DAT', 'ACTIVE')
ON CONFLICT (bank_code, account_number) DO NOTHING;
