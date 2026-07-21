-- Beneficiaries, transfer history, saga logs, outbox samples, audit logs

DELETE FROM saga_step_logs WHERE id::text LIKE 'f8888888-8888-8888-8888-%';
DELETE FROM outbox_events WHERE id::text LIKE 'f8888888-8888-8888-8888-%';
DELETE FROM audit_logs WHERE id::text LIKE 'f8888888-8888-8888-8888-%';
DELETE FROM transfer_orders WHERE id::text LIKE 'f9999999-9999-9999-9999-%';
DELETE FROM beneficiaries WHERE id::text LIKE 'faaaaaaa-aaaa-aaaa-aaaa-%';

INSERT INTO beneficiaries (
  id, user_id, nickname, account_number, account_id, currency, active, created_at, updated_at
) VALUES
(
  'faaaaaaa-aaaa-aaaa-aaaa-000000000001',
  'a1111111-1111-1111-1111-111111111101',
  'Bob rent', '1010000021', 'e7777777-7777-7777-7777-000000000021',
  'VND', TRUE, NOW() - INTERVAL '20 days', NOW()
),
(
  'faaaaaaa-aaaa-aaaa-aaaa-000000000002',
  'a1111111-1111-1111-1111-111111111101',
  'Dave freelance', '1010000041', 'e7777777-7777-7777-7777-000000000041',
  'VND', TRUE, NOW() - INTERVAL '6 days', NOW()
),
(
  'faaaaaaa-aaaa-aaaa-aaaa-000000000003',
  'a1111111-1111-1111-1111-111111111101',
  'Testuser', '1030895062', 'ad4dfb12-8068-4094-b858-bf0231897bb2',
  'VND', TRUE, NOW() - INTERVAL '2 days', NOW()
),
(
  'faaaaaaa-aaaa-aaaa-aaaa-000000000004',
  'a1111111-1111-1111-1111-111111111101',
  'Old inactive', '1010000099', NULL,
  'VND', FALSE, NOW() - INTERVAL '15 days', NOW()
),
(
  'faaaaaaa-aaaa-aaaa-aaaa-000000000011',
  'a1111111-1111-1111-1111-111111111102',
  'Alice main', '1010000011', 'e7777777-7777-7777-7777-000000000011',
  'VND', TRUE, NOW() - INTERVAL '18 days', NOW()
),
(
  'faaaaaaa-aaaa-aaaa-aaaa-000000000021',
  '783b18d0-cb61-45f4-a1fd-4030d6008755',
  'Alice', '1010000011', 'e7777777-7777-7777-7777-000000000011',
  'VND', TRUE, NOW() - INTERVAL '3 days', NOW()
)
ON CONFLICT (id) DO UPDATE SET
  nickname = EXCLUDED.nickname,
  account_number = EXCLUDED.account_number,
  account_id = EXCLUDED.account_id,
  active = EXCLUDED.active,
  updated_at = NOW();

INSERT INTO transfer_orders (
  id, idempotency_key, user_id, from_account_id, to_account_id, to_account_number,
  amount, currency, description, request_fingerprint, status, failure_reason,
  debit_entry_ref, credit_entry_ref, fee_amount, fee_entry_ref, created_at, updated_at
) VALUES
(
  'f9999999-9999-9999-9999-000000000001',
  'demo-idem-001',
  'a1111111-1111-1111-1111-111111111101',
  'e7777777-7777-7777-7777-000000000011',
  'e7777777-7777-7777-7777-000000000021',
  '1010000021',
  1000000.00, 'VND', 'Transfer to Bob - rent share', 'fp-demo-001',
  'COMPLETED', NULL,
  'e6666666-6666-6666-6666-000000000002',
  'e6666666-6666-6666-6666-000000000021',
  500000.00, 'e6666666-6666-6666-6666-000000000070',
  NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'
),
(
  'f9999999-9999-9999-9999-000000000002',
  'demo-idem-002',
  'a1111111-1111-1111-1111-111111111101',
  'e7777777-7777-7777-7777-000000000012',
  'e7777777-7777-7777-7777-000000000011',
  '1010000011',
  2500000.00, 'VND', 'Salary top-up from savings', 'fp-demo-002',
  'COMPLETED', NULL,
  'e6666666-6666-6666-6666-000000000011',
  'e6666666-6666-6666-6666-000000000004',
  0, NULL,
  NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'
),
(
  'f9999999-9999-9999-9999-000000000003',
  'demo-idem-003',
  'a1111111-1111-1111-1111-111111111101',
  'e7777777-7777-7777-7777-000000000011',
  'e7777777-7777-7777-7777-000000000041',
  '1010000041',
  2500000.00, 'VND', 'Pay Dave freelance', 'fp-demo-003',
  'COMPLETED', NULL,
  'e6666666-6666-6666-6666-000000000005',
  'e6666666-6666-6666-6666-000000000041',
  0, NULL,
  NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'
),
(
  'f9999999-9999-9999-9999-000000000004',
  'demo-idem-004',
  'a1111111-1111-1111-1111-111111111101',
  'e7777777-7777-7777-7777-000000000011',
  'ad4dfb12-8068-4094-b858-bf0231897bb2',
  '1030895062',
  1500000.00, 'VND', 'Transfer to testuser', 'fp-demo-004',
  'COMPLETED', NULL,
  'e6666666-6666-6666-6666-000000000007',
  'e6666666-6666-6666-6666-000000000051',
  0, NULL,
  NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
),
(
  'f9999999-9999-9999-9999-000000000005',
  'demo-idem-005',
  'a1111111-1111-1111-1111-111111111102',
  'e7777777-7777-7777-7777-000000000021',
  'e7777777-7777-7777-7777-000000000011',
  '1010000011',
  750000.00, 'VND', 'Coffee business settle', 'fp-demo-005',
  'COMPLETED', NULL,
  'e6666666-6666-6666-6666-000000000023',
  NULL,
  0, NULL,
  NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'
),
(
  'f9999999-9999-9999-9999-000000000006',
  'demo-idem-006',
  'a1111111-1111-1111-1111-111111111101',
  'e7777777-7777-7777-7777-000000000011',
  NULL,
  '1099999998',
  999999999.00, 'VND', 'Should fail - huge amount', 'fp-demo-006',
  'FAILED', 'INSUFFICIENT_FUNDS',
  NULL, NULL, 0, NULL,
  NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'
),
(
  'f9999999-9999-9999-9999-000000000007',
  'demo-idem-007',
  'a1111111-1111-1111-1111-111111111103',
  'e7777777-7777-7777-7777-000000000032',
  'e7777777-7777-7777-7777-000000000021',
  '1010000021',
  100000.00, 'VND', 'Attempt from frozen account', 'fp-demo-007',
  'FAILED', 'ACCOUNT_FROZEN',
  NULL, NULL, 0, NULL,
  NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'
)
ON CONFLICT (id) DO UPDATE SET
  status = EXCLUDED.status,
  failure_reason = EXCLUDED.failure_reason,
  updated_at = NOW();

INSERT INTO saga_step_logs (id, transfer_id, step, status, detail, created_at) VALUES
('f8888888-8888-8888-8888-000000000001', 'f9999999-9999-9999-9999-000000000001', 'VALIDATE', 'OK', 'ok', NOW() - INTERVAL '20 days'),
('f8888888-8888-8888-8888-000000000002', 'f9999999-9999-9999-9999-000000000001', 'DEBIT', 'OK', 'debited', NOW() - INTERVAL '20 days' + INTERVAL '1 second'),
('f8888888-8888-8888-8888-000000000003', 'f9999999-9999-9999-9999-000000000001', 'CREDIT', 'OK', 'credited', NOW() - INTERVAL '20 days' + INTERVAL '2 seconds'),
('f8888888-8888-8888-8888-000000000004', 'f9999999-9999-9999-9999-000000000001', 'FEE', 'OK', 'fee posted', NOW() - INTERVAL '20 days' + INTERVAL '3 seconds'),
('f8888888-8888-8888-8888-000000000005', 'f9999999-9999-9999-9999-000000000006', 'VALIDATE', 'OK', 'ok', NOW() - INTERVAL '4 days'),
('f8888888-8888-8888-8888-000000000006', 'f9999999-9999-9999-9999-000000000006', 'DEBIT', 'FAILED', 'INSUFFICIENT_FUNDS', NOW() - INTERVAL '4 days' + INTERVAL '1 second'),
('f8888888-8888-8888-8888-000000000007', 'f9999999-9999-9999-9999-000000000007', 'VALIDATE', 'FAILED', 'ACCOUNT_FROZEN', NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO outbox_events (
  id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at
) VALUES
(
  'f8888888-8888-8888-8888-100000000001',
  'TRANSFER', 'f9999999-9999-9999-9999-000000000001',
  'TransferCompleted',
  '{"transferId":"f9999999-9999-9999-9999-000000000001","amount":1000000}',
  NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days' + INTERVAL '5 seconds'
),
(
  'f8888888-8888-8888-8888-100000000002',
  'TRANSFER', 'f9999999-9999-9999-9999-000000000004',
  'TransferCompleted',
  '{"transferId":"f9999999-9999-9999-9999-000000000004","amount":1500000}',
  NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '3 seconds'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_logs (
  id, actor_user_id, action, resource_type, resource_id, ip, metadata, created_at
) VALUES
(
  'f8888888-8888-8888-8888-200000000001',
  'a1111111-1111-1111-1111-111111111101',
  'TRANSFER_CREATE', 'TRANSFER', 'f9999999-9999-9999-9999-000000000001',
  '127.0.0.1', '{"demo":true}', NOW() - INTERVAL '20 days'
),
(
  'f8888888-8888-8888-8888-200000000002',
  '878ffb97-21d5-48a5-893b-bf03e9d07914',
  'ACCOUNT_FREEZE', 'ACCOUNT', 'e7777777-7777-7777-7777-000000000032',
  '127.0.0.1', '{"reason":"demo freeze"}', NOW() - INTERVAL '12 days'
)
ON CONFLICT (id) DO NOTHING;
