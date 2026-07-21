DELETE FROM notification_logs WHERE id::text LIKE 'fbbbbbbb-bbbb-bbbb-bbbb-%';
DELETE FROM processed_events WHERE event_id::text LIKE 'f8888888-8888-8888-8888-%';

INSERT INTO processed_events (event_id, processed_at) VALUES
('f8888888-8888-8888-8888-100000000001', NOW() - INTERVAL '20 days'),
('f8888888-8888-8888-8888-100000000002', NOW() - INTERVAL '1 day')
ON CONFLICT (event_id) DO NOTHING;

INSERT INTO notification_logs (
  id, event_id, channel, recipient, template, status, body, created_at
) VALUES
(
  'fbbbbbbb-bbbb-bbbb-bbbb-000000000001',
  'f8888888-8888-8888-8888-100000000001',
  'EMAIL', 'alice@demo.local', 'transfer_completed', 'SENT',
  'Transfer 1,000,000 VND to Bob completed', NOW() - INTERVAL '20 days'
),
(
  'fbbbbbbb-bbbb-bbbb-bbbb-000000000002',
  'f8888888-8888-8888-8888-100000000002',
  'EMAIL', 'alice@demo.local', 'transfer_completed', 'SENT',
  'Transfer 1,500,000 VND to testuser completed', NOW() - INTERVAL '1 day'
),
(
  'fbbbbbbb-bbbb-bbbb-bbbb-000000000003',
  'aaaaaaaa-aaaa-aaaa-aaaa-000000000099',
  'EMAIL', 'dave@demo.local', 'password_reset', 'SENT',
  'Your password was reset by support (demo)', NOW() - INTERVAL '6 days'
)
ON CONFLICT (id) DO NOTHING;
