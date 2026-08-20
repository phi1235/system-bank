-- Separate read-only Wealth access from customer Auto-Sweep configuration changes.
INSERT INTO permissions (code, description) VALUES
  ('ib:wealth:auto-sweep:manage', 'IB - Configure and pause Auto-Sweep')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
VALUES ('CUSTOMER', 'ib:wealth:auto-sweep:manage')
ON CONFLICT DO NOTHING;
