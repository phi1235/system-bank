-- Back-office ops notification inbox (shared staff alerts)
INSERT INTO permissions (code, description) VALUES
  ('notifications:ops:view', 'BO — view ops notification alerts')
ON CONFLICT (code) DO NOTHING;

-- SUPER_ADMIN / ADMIN already receive all permissions via existing seed patterns;
-- still grant explicitly for clarity and for environments that re-run role grants.
INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'OPS_ADMIN', 'SUPPORT', 'COMPLIANCE')
  AND p.code = 'notifications:ops:view'
ON CONFLICT DO NOTHING;
