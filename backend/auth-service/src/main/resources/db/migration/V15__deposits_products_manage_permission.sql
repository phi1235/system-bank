-- Deposit product-rate management (account-service). More sensitive than viewing:
-- grant follows freeze/unfreeze — roles allowed to execute account operations.

INSERT INTO permissions (code, description) VALUES
  ('deposits:products:manage', 'Deposits · manage product rates & availability')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, 'deposits:products:manage'
FROM role_permissions rp
WHERE rp.permission_code = 'accounts:freeze:execute'
ON CONFLICT DO NOTHING;
