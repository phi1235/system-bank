-- Maker-checker for support tickets:
--   claim  = maker (pick up / work the ticket)
--   decide = checker (resolve / reject) — must be a different staff user
INSERT INTO permissions (code, description) VALUES
  ('support:tickets:claim', 'BO — claim/assign support tickets (maker)'),
  ('support:tickets:decide', 'BO — resolve/reject support tickets (checker)')
ON CONFLICT (code) DO NOTHING;

-- Ensure list still present (idempotent)
INSERT INTO permissions (code, description) VALUES
  ('support:tickets:list', 'BO — list/search support tickets')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN (VALUES
  ('support:tickets:list'),
  ('support:tickets:claim'),
  ('support:tickets:decide')
) AS p(code)
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'OPS_ADMIN', 'SUPPORT')
ON CONFLICT DO NOTHING;
