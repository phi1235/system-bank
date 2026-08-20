-- Remove unused corporate permissions that are not enforced by any controller.
-- Approval is handled by corporate role matching in ApprovalWorkflowService, not via JWT permissions.
-- Audit view controller does not exist yet.

DELETE FROM role_permissions
WHERE permission_code IN (
    'corp:payout:approve:checker',
    'corp:payout:approve:cfo',
    'corp:payout:approve:chairman',
    'corp:audit:view'
);

DELETE FROM permissions WHERE code IN (
    'corp:payout:approve:checker',
    'corp:payout:approve:cfo',
    'corp:payout:approve:chairman',
    'corp:audit:view'
);
