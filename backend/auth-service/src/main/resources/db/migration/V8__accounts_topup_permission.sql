-- Back-office admin cash-in / top-up permission
INSERT INTO permissions (code, description) VALUES
  ('accounts:topup:execute', 'Accounts — admin cash-in / top-up')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, 'accounts:topup:execute'
FROM roles r
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'OPS_ADMIN')
ON CONFLICT DO NOTHING;
