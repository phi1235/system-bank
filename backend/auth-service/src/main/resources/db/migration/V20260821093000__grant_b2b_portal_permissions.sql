-- Ensure business:dashboard:view and b2b:openbanking:* are granted to CUSTOMER and business roles for full portal access
INSERT INTO role_permissions (role_code, permission_code)
VALUES
  ('CUSTOMER', 'business:dashboard:view'),
  ('CUSTOMER', 'b2b:openbanking:apps:view'),
  ('CUSTOMER', 'b2b:openbanking:apps:manage'),
  ('CUSTOMER', 'b2b:openbanking:consents:view'),
  ('CUSTOMER', 'b2b:openbanking:consents:manage'),
  ('CUSTOMER', 'b2b:openbanking:sandbox:use'),
  ('CUSTOMER', 'b2b:openbanking:logs:view'),
  ('BUSINESS_OWNER', 'business:dashboard:view'),
  ('BUSINESS_FINANCE', 'business:dashboard:view'),
  ('BUSINESS_OPERATOR', 'business:dashboard:view'),
  ('BUSINESS_VIEWER', 'business:dashboard:view')
ON CONFLICT DO NOTHING;
