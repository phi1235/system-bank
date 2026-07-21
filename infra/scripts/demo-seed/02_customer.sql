-- Customer profiles (id = auth user id)

INSERT INTO customers (
  id, full_name, phone, email, national_id_encrypted, kyc_status, address, created_at, updated_at
) VALUES
(
  '783b18d0-cb61-45f4-a1fd-4030d6008755',
  'Test User Demo', '0901000001', 'testuser@demo.local', NULL,
  'VERIFIED', '12 Nguyen Hue, Q1, HCMC',
  NOW() - INTERVAL '40 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111101',
  'Alice Nguyen', '0901111101', 'alice@demo.local', NULL,
  'VERIFIED', '88 Le Loi, Q1, HCMC',
  NOW() - INTERVAL '35 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111102',
  'Bob Tran', '0901111102', 'bob@demo.local', NULL,
  'VERIFIED', '15 Hai Ba Trung, HN',
  NOW() - INTERVAL '30 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111103',
  'Carol Le', '0901111103', 'carol@demo.local', NULL,
  'PENDING', '3 Tran Phu, Da Nang',
  NOW() - INTERVAL '25 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111104',
  'Dave Pham', '0901111104', 'dave@demo.local', NULL,
  'REJECTED', '9 Nguyen Van Linh, Can Tho',
  NOW() - INTERVAL '20 days', NOW()
),
(
  'c3333333-3333-3333-3333-333333333301',
  'Locked User', '0901999901', 'locked@demo.local', NULL,
  'PENDING', 'N/A',
  NOW() - INTERVAL '10 days', NOW()
),
(
  'c3333333-3333-3333-3333-333333333302',
  'Must Change Password', '0901999902', 'mustchange@demo.local', NULL,
  'VERIFIED', '1 Demo Street',
  NOW() - INTERVAL '5 days', NOW()
)
ON CONFLICT (id) DO UPDATE SET
  full_name = EXCLUDED.full_name,
  phone = EXCLUDED.phone,
  email = EXCLUDED.email,
  kyc_status = EXCLUDED.kyc_status,
  address = EXCLUDED.address,
  updated_at = NOW();
