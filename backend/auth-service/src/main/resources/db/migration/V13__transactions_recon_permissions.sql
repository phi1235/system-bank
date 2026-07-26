-- End-of-day reconciliation (transaction-service): view runs/discrepancies + trigger manual run.
-- Grant follows the monitor, same policy as V12: roles that can view the transaction list.

INSERT INTO permissions (code, description) VALUES
  ('transactions:recon:view',    'Reconciliation · view runs & discrepancies'),
  ('transactions:recon:execute', 'Reconciliation · trigger manual run')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, p.code
FROM role_permissions rp
CROSS JOIN (VALUES
  ('transactions:recon:view'),
  ('transactions:recon:execute')
) AS p(code)
WHERE rp.permission_code = 'transactions:list:view'
ON CONFLICT DO NOTHING;
