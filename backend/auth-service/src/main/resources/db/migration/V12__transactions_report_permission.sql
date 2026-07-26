-- Admin transaction report dashboard (MyBatis read model in transaction-service).
-- Grant follows the monitor: every role that can view the transaction list can view the report.

INSERT INTO permissions (code, description) VALUES
  ('transactions:report:view', 'Transactions · view report dashboard')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, 'transactions:report:view'
FROM role_permissions rp
WHERE rp.permission_code = 'transactions:list:view'
ON CONFLICT DO NOTHING;
