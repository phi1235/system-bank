INSERT INTO permissions (code, description) VALUES
  ('customers:kyc:review',  'Customers - perform KYC maker review'),
  ('customers:kyc:approve', 'Customers - perform independent KYC checker decision')
ON CONFLICT (code) DO NOTHING;

-- Preserve current access while splitting duties. Runtime blocks the same user acting twice.
INSERT INTO role_permissions (role_code, permission_code)
SELECT existing.role_code, target.code
FROM role_permissions existing
JOIN permissions target ON target.code IN ('customers:kyc:review', 'customers:kyc:approve')
WHERE existing.permission_code = 'customers:kyc:decide'
ON CONFLICT DO NOTHING;
