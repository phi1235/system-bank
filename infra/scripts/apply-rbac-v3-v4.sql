-- Manual apply of V3+V4 RBAC (idempotent)

INSERT INTO permissions (code, description) VALUES
  ('dashboard:view',            'Dashboard · open overview'),
  ('customers:list:view',       'Customers · view list'),
  ('customers:kyc:decide',      'Customers · decide KYC status'),
  ('accounts:lookup:view',      'Accounts · lookup detail'),
  ('accounts:freeze:execute',   'Accounts · freeze / unfreeze'),
  ('transactions:list:view',    'Transactions · monitor list'),
  ('audit:list:view',           'Audit · view log'),
  ('rbac:access',               'RBAC · open module'),
  ('rbac:users:assign',         'RBAC · assign roles to users'),
  ('rbac:roles:manage',         'RBAC · create/edit roles & permissions'),
  ('risk:view',                 'Risk · open module')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, m.new_code
FROM role_permissions rp
JOIN (
  VALUES
    ('dashboard:read', 'dashboard:view'),
    ('customers:read', 'customers:list:view'),
    ('customers:kyc_decide', 'customers:kyc:decide'),
    ('accounts:read', 'accounts:lookup:view'),
    ('accounts:freeze', 'accounts:freeze:execute'),
    ('tx:monitor', 'transactions:list:view'),
    ('audit:read', 'audit:list:view'),
    ('rbac:manage', 'rbac:access'),
    ('rbac:manage', 'rbac:users:assign'),
    ('rbac:manage', 'rbac:roles:manage'),
    ('risk:read', 'risk:view')
) AS m(old_code, new_code) ON rp.permission_code = m.old_code
ON CONFLICT DO NOTHING;

DELETE FROM role_permissions WHERE permission_code IN (
  'dashboard:read', 'customers:read', 'customers:kyc_decide',
  'accounts:read', 'accounts:freeze', 'tx:monitor',
  'audit:read', 'rbac:manage', 'risk:read'
);

DELETE FROM permissions WHERE code IN (
  'dashboard:read', 'customers:read', 'customers:kyc_decide',
  'accounts:read', 'accounts:freeze', 'tx:monitor',
  'audit:read', 'rbac:manage', 'risk:read'
);

INSERT INTO permissions (code, description) VALUES
  ('ib:home:view',         'IB · Home overview'),
  ('ib:accounts:view',     'IB · View my accounts'),
  ('ib:accounts:open',     'IB · Open payment/savings account'),
  ('ib:transfer:view',     'IB · Open transfer form'),
  ('ib:transfer:execute',  'IB · Submit internal transfer'),
  ('ib:history:view',      'IB · Transfer history'),
  ('ib:profile:view',      'IB · View profile'),
  ('ib:profile:edit',      'IB · Update profile'),
  ('ib:profile:mfa',       'IB · Setup / enable MFA'),
  ('ib:cards:view',        'IB · Cards (placeholder)'),
  ('ib:wealth:view',       'IB · Wealth (placeholder)'),
  ('ib:support:view',      'IB · Support (placeholder)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER', p.code
FROM permissions p
WHERE p.code LIKE 'ib:%'
ON CONFLICT DO NOTHING;

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
SELECT COALESCE(MAX(installed_rank),0)+1, '3', 'rbac granular', 'SQL', 'V3__rbac_granular.sql', NULL, 'bank', NOW(), 0, true
FROM flyway_schema_history
WHERE NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '3');

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
SELECT COALESCE(MAX(installed_rank),0)+1, '4', 'ib customer permissions', 'SQL', 'V4__ib_customer_permissions.sql', NULL, 'bank', NOW(), 0, true
FROM flyway_schema_history
WHERE NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '4');
