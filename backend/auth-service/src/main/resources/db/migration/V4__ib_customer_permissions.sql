-- Internet Banking (customer portal) permissions + default grant for CUSTOMER role

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

-- Default full IB package for CUSTOMER role
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER', p.code
FROM permissions p
WHERE p.code LIKE 'ib:%'
ON CONFLICT DO NOTHING;
