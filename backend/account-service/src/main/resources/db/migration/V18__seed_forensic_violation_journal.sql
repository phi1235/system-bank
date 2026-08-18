-- Seed demo accounts if not exists
INSERT INTO accounts (
    id, user_id, account_number, account_type, currency, balance, status, created_at, updated_at
) VALUES 
(
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '1000000001',
    'PAYMENT',
    'VND',
    50000000.00,
    'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP - INTERVAL '1 day'
),
(
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    '1000000002',
    'PAYMENT',
    'VND',
    20000000.00,
    'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP - INTERVAL '1 day'
) ON CONFLICT (id) DO NOTHING;

-- Seed reversal journal missing original reference (triggers INV-REVERSAL-001 in Forensics)
INSERT INTO ledger_journals (
    id, business_command_id, business_reference, journal_type, status, currency, description, sequence_no, created_at, posted_at
) VALUES (
    'f1111111-2222-4321-8888-999999999999',
    'ffffffff-0000-4000-a000-000000000001',
    'TX-DEMO-FAULTY-001',
    'REVERSAL',
    'POSTED',
    'VND',
    'DEMO UNLINKED REVERSAL JOURNAL FOR FORENSICS VERIFICATION TEST',
    99999,
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP - INTERVAL '1 hour'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_postings (
    id, journal_id, account_id, ledger_account_code, side, amount, currency, created_at
) VALUES 
(
    'f2222222-2222-4321-8888-000000000001',
    'f1111111-2222-4321-8888-999999999999',
    '22222222-2222-2222-2222-222222222222',
    '1011',
    'DEBIT',
    5000000.00,
    'VND',
    CURRENT_TIMESTAMP - INTERVAL '1 hour'
),
(
    'f3333333-3333-4321-8888-000000000001',
    'f1111111-2222-4321-8888-999999999999',
    '33333333-3333-3333-3333-333333333333',
    '1011',
    'CREDIT',
    5000000.00,
    'VND',
    CURRENT_TIMESTAMP - INTERVAL '1 hour'
) ON CONFLICT (id) DO NOTHING;
