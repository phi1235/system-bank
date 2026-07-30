-- Bill Payments: ib:bills:view + ib:bills:execute permissions
-- Separate permission for Bill Payments & Mobile Top-up feature

INSERT INTO permissions (code, description) VALUES
  ('ib:bills:view',    'IB · View Bill Payments & Top-up'),
  ('ib:bills:execute', 'IB · Execute bill payment / top-up')
ON CONFLICT (code) DO NOTHING;

-- Grant to CUSTOMER role by default
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER', p.code
FROM permissions p
WHERE p.code IN ('ib:bills:view', 'ib:bills:execute')
ON CONFLICT DO NOTHING;
