INSERT INTO permissions (code, description) VALUES
  ('corp:portal:view', 'Corporate portal - view dashboard and accounts'),
  ('corp:payout:create', 'Corporate portal - create and upload payout batches'),
  ('corp:payout:submit', 'Corporate portal - submit payout batch for approval'),
  ('corp:payout:approve:checker', 'Corporate portal - approve payout as Checker'),
  ('corp:payout:approve:cfo', 'Corporate portal - approve payout as CFO'),
  ('corp:payout:approve:chairman', 'Corporate portal - approve payout as Chairman'),
  ('corp:payout:cancel', 'Corporate portal - cancel payout batch'),
  ('corp:payout:retry', 'Corporate portal - retry failed payout items'),
  ('corp:matrix:view', 'Corporate portal - view approval matrix policies'),
  ('corp:matrix:manage', 'Corporate portal - create and update approval matrix policies'),
  ('corp:receipt:download', 'Corporate portal - download payout receipts and reports'),
  ('corp:audit:view', 'Corporate portal - view corporate audit logs')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT role.code, permission.code
FROM roles role
JOIN permissions permission ON permission.code LIKE 'corp:%'
WHERE role.code IN ('CUSTOMER', 'SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
