-- Granular permissions: screen → feature → action
-- Migrates legacy coarse codes to detailed codes.

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

-- Copy grants: old → new (keep roles that had old perms)
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

-- Drop legacy coarse permissions
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
