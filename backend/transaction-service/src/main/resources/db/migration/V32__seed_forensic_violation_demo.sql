-- Seed demo transaction with invariant violation for Financial Forensics testing
INSERT INTO transfer_orders (
    id, user_id, from_account_id, to_account_id, to_account_number,
    amount, currency, description, request_fingerprint, status,
    failure_reason, idempotency_key, created_at, updated_at
) VALUES (
    'ffffffff-0000-4000-a000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '1000000002',
    5000000.00,
    'VND',
    'DEMO FAULTY TRANSFER FOR FORENSICS TEST',
    'demo-request-fingerprint-001',
    'FAILED',
    'SAGA_STEP_FAILED: Ledger balance mismatch detected during debit',
    'demo-faulty-idempotency-key-001',
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP - INTERVAL '1 hour'
) ON CONFLICT (id) DO NOTHING;

-- Seed an unhandled violation finding for this transaction
INSERT INTO forensic_findings (
    id, finding_key, transaction_id, rule_code, subject_type, subject_id,
    outcome, severity, disposition, title, detail, evidence_json, evidence_hash, version,
    detected_at, last_seen_at
) VALUES (
    'a1b2c3d4-e5f6-4000-8000-112233445566',
    'INV-REVERSAL-001:ffffffff-0000-4000-a000-000000000001',
    'ffffffff-0000-4000-a000-000000000001',
    'INV-REVERSAL-001',
    'TRANSACTION',
    'ffffffff-0000-4000-a000-000000000001',
    'FAIL',
    'CRITICAL',
    'UNREVIEWED',
    'Reversal journal missing original reference',
    'Reversal does not reference an original journal',
    '["ffffffff-0000-4000-a000-000000000001"]'::jsonb,
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    0,
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP - INTERVAL '1 hour'
) ON CONFLICT (id) DO NOTHING;
