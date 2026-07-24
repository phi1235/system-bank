-- Support ticket staff permissions + keep ib:support:view for customer portal
INSERT INTO permissions (code, description) VALUES
  ('support:tickets:list', 'BO — list/search support tickets'),
  ('support:tickets:decide', 'BO — approve/resolve/reject support tickets')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN (VALUES
  ('support:tickets:list'),
  ('support:tickets:decide')
) AS p(code)
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'OPS_ADMIN', 'SUPPORT')
ON CONFLICT DO NOTHING;

-- Ensure CUSTOMER keeps support module access (seeded earlier as placeholder view)
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER', 'ib:support:view'
WHERE EXISTS (SELECT 1 FROM permissions WHERE code = 'ib:support:view')
ON CONFLICT DO NOTHING;
