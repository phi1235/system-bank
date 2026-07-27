-- Card approval (account-service): staff decide customer card requests (approve/reject).
-- Execute-level grant follows freeze/unfreeze, same policy as deposit product management.

INSERT INTO permissions (code, description) VALUES
  ('cards:approve:execute', 'Cards · approve/reject card requests')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, 'cards:approve:execute'
FROM role_permissions rp
WHERE rp.permission_code = 'accounts:freeze:execute'
ON CONFLICT DO NOTHING;
