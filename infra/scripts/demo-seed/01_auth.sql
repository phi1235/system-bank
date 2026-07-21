-- Demo users (password = Demo1234! for all rows below; admin left alone)
-- Hashes generated with BoundPasswordEncoder material + BCrypt cost 12 + local PASSWORD_PEPPER.

-- testuser (existing) → reset password + keep CUSTOMER
INSERT INTO users (
  id, username, email, password_hash, roles, enabled, mfa_enabled,
  must_change_password, locked_reason, created_at, updated_at
) VALUES (
  '783b18d0-cb61-45f4-a1fd-4030d6008755',
  'testuser',
  'testuser@demo.local',
  '$2a$12$JjJll.haBYhVj1vnIk.GfeoRf4Rx4atYxps9.Llbwqhm.L4VyZPNG',
  'CUSTOMER', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '40 days', NOW()
)
ON CONFLICT (id) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  email = EXCLUDED.email,
  roles = EXCLUDED.roles,
  enabled = TRUE,
  must_change_password = FALSE,
  locked_reason = NULL,
  updated_at = NOW();

INSERT INTO users (
  id, username, email, password_hash, roles, enabled, mfa_enabled,
  must_change_password, locked_reason, created_at, updated_at
) VALUES
(
  'a1111111-1111-1111-1111-111111111101',
  'alice', 'alice@demo.local',
  '$2a$12$IiuoJElmR35i9Aq8jMM4hOsfnKZR0LwkTw3LuKZUfjR2Z1h1c2Jd6',
  'CUSTOMER', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '35 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111102',
  'bob', 'bob@demo.local',
  '$2a$12$fgu0UU4IGhseKl5T0JOqhewc9XfAFHhhgPBMc0oJNKzIRt9uZBhYq',
  'CUSTOMER', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '30 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111103',
  'carol', 'carol@demo.local',
  '$2a$12$k9AhmSyMdOJUsrUqiAXYpeavsvOUH5yatdayLuIqDMj6tjxHDAE62',
  'CUSTOMER', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '25 days', NOW()
),
(
  'a1111111-1111-1111-1111-111111111104',
  'dave', 'dave@demo.local',
  '$2a$12$UCE5c7VLG3VZ4awMTbyCqOfGEawHLLaWjvwSJvt0B5HRiEF0jPbAe',
  'CUSTOMER', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '20 days', NOW()
),
(
  'b2222222-2222-2222-2222-222222222201',
  'opsadmin', 'opsadmin@demo.local',
  '$2a$12$zIL1ygP88F2yhoom4hzHoOmp48VrZUFRpu2pipv16JZlzYMd4kTpu',
  'OPS_ADMIN', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '60 days', NOW()
),
(
  'b2222222-2222-2222-2222-222222222202',
  'kyc1', 'kyc1@demo.local',
  '$2a$12$DK672hLbCYz6rDaY/mpdRuEFL6fJIwZ4Nvv2BGlbDD.rKlyeuvX7S',
  'KYC_OFFICER', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '55 days', NOW()
),
(
  'b2222222-2222-2222-2222-222222222203',
  'support1', 'support1@demo.local',
  '$2a$12$ljjf4BAqEFbxXJsZCfZQGerfXy0wKObDuEO9G4k1THmfl9kIFKSp2',
  'SUPPORT', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '50 days', NOW()
),
(
  'b2222222-2222-2222-2222-222222222204',
  'auditor1', 'auditor1@demo.local',
  '$2a$12$2JVwv/91JwrGsHUip5z4LOpw9jh0ysvq14M5Yp0zOp7HF/Owv6IgG',
  'AUDITOR', TRUE, FALSE, FALSE, NULL,
  NOW() - INTERVAL '45 days', NOW()
),
(
  'c3333333-3333-3333-3333-333333333301',
  'lockeduser', 'locked@demo.local',
  '$2a$12$ffxdWJ0rsbi.49ENBYNEnO2iX8/S4grm7rJR/BRRPpCDVdp6NJJDy',
  'CUSTOMER', FALSE, FALSE, FALSE, 'Demo: locked for suspicious login',
  NOW() - INTERVAL '10 days', NOW()
),
(
  'c3333333-3333-3333-3333-333333333302',
  'mustchange', 'mustchange@demo.local',
  '$2a$12$rFubCEyON5wMNbtRs38JJ.J87E0FVKi/cCrtUJD3kt8Y36M2yKiKS',
  'CUSTOMER', TRUE, FALSE, TRUE, NULL,
  NOW() - INTERVAL '5 days', NOW()
)
ON CONFLICT (id) DO UPDATE SET
  username = EXCLUDED.username,
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  roles = EXCLUDED.roles,
  enabled = EXCLUDED.enabled,
  must_change_password = EXCLUDED.must_change_password,
  locked_reason = EXCLUDED.locked_reason,
  updated_at = NOW();

-- Auth audit samples
DELETE FROM auth_audit_log WHERE id::text LIKE 'd4444444-4444-4444-4444-%';
INSERT INTO auth_audit_log (id, user_id, action, ip, detail, created_at) VALUES
('d4444444-4444-4444-4444-000000000001', 'a1111111-1111-1111-1111-111111111101', 'LOGIN', '127.0.0.1', 'demo seed', NOW() - INTERVAL '2 days'),
('d4444444-4444-4444-4444-000000000002', 'a1111111-1111-1111-1111-111111111101', 'LOGIN', '127.0.0.1', 'demo seed', NOW() - INTERVAL '1 day'),
('d4444444-4444-4444-4444-000000000003', 'a1111111-1111-1111-1111-111111111102', 'LOGIN', '10.0.0.12', 'demo seed', NOW() - INTERVAL '12 hours'),
('d4444444-4444-4444-4444-000000000004', '783b18d0-cb61-45f4-a1fd-4030d6008755', 'REGISTER', '127.0.0.1', 'username=testuser', NOW() - INTERVAL '40 days'),
('d4444444-4444-4444-4444-000000000005', 'c3333333-3333-3333-3333-333333333301', 'LOGIN_FAIL', '203.0.113.9', 'demo locked user', NOW() - INTERVAL '3 days');

-- Password reset ticket samples
DELETE FROM password_reset_tickets WHERE id::text LIKE 'd5555555-5555-5555-5555-%';
INSERT INTO password_reset_tickets (
  id, user_id, username, email, channel, status, requester_note,
  created_at, fulfilled_at, fulfilled_by, rejected_at, rejected_by, reject_reason
) VALUES
(
  'd5555555-5555-5555-5555-000000000001',
  'c3333333-3333-3333-3333-333333333302',
  'mustchange', 'mustchange@demo.local', 'EMAIL', 'OPEN',
  'Forgot password after travel',
  NOW() - INTERVAL '1 day', NULL, NULL, NULL, NULL, NULL
),
(
  'd5555555-5555-5555-5555-000000000002',
  'a1111111-1111-1111-1111-111111111104',
  'dave', 'dave@demo.local', 'EMAIL', 'FULFILLED',
  'Reset via support',
  NOW() - INTERVAL '7 days', NOW() - INTERVAL '6 days',
  '878ffb97-21d5-48a5-893b-bf03e9d07914', NULL, NULL, NULL
);
