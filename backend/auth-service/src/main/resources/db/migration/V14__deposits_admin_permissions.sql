-- Term-deposit back office (account-service): funding summary + manual accrual/maturity batch.
-- Grant follows the account lookup: roles that can inspect accounts can operate deposits.

INSERT INTO permissions (code, description) VALUES
  ('deposits:summary:view', 'Deposits · view funding summary'),
  ('deposits:batch:execute', 'Deposits · run accrual/maturity batch')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, p.code
FROM role_permissions rp
CROSS JOIN (VALUES
  ('deposits:summary:view'),
  ('deposits:batch:execute')
) AS p(code)
WHERE rp.permission_code = 'accounts:lookup:view'
ON CONFLICT DO NOTHING;
