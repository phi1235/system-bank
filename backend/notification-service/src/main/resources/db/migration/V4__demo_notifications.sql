-- Demo inbox rows for local UX (customer IB + admin ops).
-- Idempotent via unique event_id. Safe to re-run after migrate.

-- Known demo customers from auth seed (infra demo users)
-- alice = a1111111-1111-1111-1111-111111111101
-- bob   = a1111111-1111-1111-1111-111111111102

INSERT INTO notification_logs (
  id, event_id, channel, recipient, template, status, body,
  user_id, audience, read_at, created_at
) VALUES
  (
    'd1111111-1111-1111-1111-111111111101',
    'e1111111-1111-1111-1111-111111111101',
    'EMAIL',
    'alice@bank.local',
    'TRANSFER_COMPLETED',
    'SENT',
    'Your transfer txn-demo-1001 of 250000 VND completed successfully. Demo seed.',
    'a1111111-1111-1111-1111-111111111101',
    'CUSTOMER',
    NULL,
    NOW() - INTERVAL '2 hours'
  ),
  (
    'd1111111-1111-1111-1111-111111111102',
    'e1111111-1111-1111-1111-111111111102',
    'EMAIL',
    'alice@bank.local',
    'TRANSFER_FAILED',
    'SENT',
    'Your transfer txn-demo-1002 ended as FAILED. Reason: insufficient funds (demo). Amount: 5000000 VND',
    'a1111111-1111-1111-1111-111111111101',
    'CUSTOMER',
    NULL,
    NOW() - INTERVAL '90 minutes'
  ),
  (
    'd1111111-1111-1111-1111-111111111103',
    'e1111111-1111-1111-1111-111111111103',
    'EMAIL',
    'alice@bank.local',
    'TRANSFER_COMPLETED',
    'SENT',
    'Your transfer txn-demo-1003 of 75000 VND completed successfully. Coffee money (demo).',
    'a1111111-1111-1111-1111-111111111101',
    'CUSTOMER',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
  ),
  (
    'd1111111-1111-1111-1111-111111111104',
    'e1111111-1111-1111-1111-111111111104',
    'EMAIL',
    'bob@bank.local',
    'TRANSFER_COMPLETED',
    'SENT',
    'Your transfer txn-demo-2001 of 1000000 VND completed successfully. Demo seed for bob.',
    'a1111111-1111-1111-1111-111111111102',
    'CUSTOMER',
    NULL,
    NOW() - INTERVAL '45 minutes'
  ),
  (
    'd2222222-2222-2222-2222-222222222201',
    'e2222222-2222-2222-2222-222222222201',
    'OPS',
    'ops@bank.local',
    'OPS_TRANSFER_FAILED',
    'OPEN',
    'Transfer failed txn=txn-demo-1002 amount=5000000 VND userId=a1111111-1111-1111-1111-111111111101 status=FAILED reason=insufficient funds (demo)',
    NULL,
    'OPS',
    NULL,
    NOW() - INTERVAL '85 minutes'
  ),
  (
    'd2222222-2222-2222-2222-222222222202',
    'e2222222-2222-2222-2222-222222222202',
    'OPS',
    'ops@bank.local',
    'OPS_TRANSFER_FAILED',
    'OPEN',
    'Transfer failed txn=txn-demo-3001 amount=12000000 VND userId=a1111111-1111-1111-1111-111111111103 status=COMPENSATED reason=credit timeout (demo)',
    NULL,
    'OPS',
    NULL,
    NOW() - INTERVAL '30 minutes'
  ),
  (
    'd2222222-2222-2222-2222-222222222203',
    'e2222222-2222-2222-2222-222222222203',
    'OPS',
    'ops@bank.local',
    'OPS_TRANSFER_FAILED',
    'OPEN',
    'Transfer failed txn=txn-demo-3002 amount=999000 VND userId=a1111111-1111-1111-1111-111111111104 status=FAILED reason=account frozen (demo)',
    NULL,
    'OPS',
    NOW() - INTERVAL '3 hours',
    NOW() - INTERVAL '4 hours'
  )
ON CONFLICT (event_id) DO NOTHING;
