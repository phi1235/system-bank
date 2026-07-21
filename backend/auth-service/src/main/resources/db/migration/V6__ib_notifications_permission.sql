-- Internet Banking: customer notification inbox

INSERT INTO permissions (code, description) VALUES
  ('ib:notifications:view', 'IB · View notification inbox')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER', p.code
FROM permissions p
WHERE p.code = 'ib:notifications:view'
ON CONFLICT DO NOTHING;
