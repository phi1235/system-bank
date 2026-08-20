-- Clean up sensitive corporate permissions erroneously granted to standard CUSTOMER role
DELETE FROM role_permissions 
WHERE role_code = 'CUSTOMER' 
  AND permission_code LIKE 'corp:%' 
  AND permission_code != 'corp:portal:view';
