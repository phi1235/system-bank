-- Split IB support: view (list/detail mine) vs create (open ticket)
-- Admin assigns either/both via RBAC matrix; not hard-coded to one action.

INSERT INTO permissions (code, description) VALUES
  ('ib:support:view',   'IB · View my support tickets'),
  ('ib:support:create', 'IB · Create support ticket')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

-- Default: CUSTOMER gets both (same as previous combined behavior)
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER', p.code
FROM (VALUES
  ('ib:support:view'),
  ('ib:support:create')
) AS p(code)
WHERE EXISTS (SELECT 1 FROM roles WHERE code = 'CUSTOMER')
  AND EXISTS (SELECT 1 FROM permissions WHERE code = p.code)
ON CONFLICT DO NOTHING;
