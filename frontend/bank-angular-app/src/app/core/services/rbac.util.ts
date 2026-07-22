/** Staff roles that may access Back Office (realm BACK_OFFICE). */
export const STAFF_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'OPS_ADMIN',
  'KYC_OFFICER',
  'COMPLIANCE',
  'SUPPORT',
  'AUDITOR',
] as const;

export type StaffRole = (typeof STAFF_ROLES)[number];

/** Granular permission codes */
export const PERMISSIONS = {
  // Back Office
  DASHBOARD_VIEW: 'dashboard:view',
  CUSTOMERS_LIST_VIEW: 'customers:list:view',
  CUSTOMERS_KYC_DECIDE: 'customers:kyc:decide',
  ACCOUNTS_LOOKUP_VIEW: 'accounts:lookup:view',
  ACCOUNTS_FREEZE_EXECUTE: 'accounts:freeze:execute',
  ACCOUNTS_TOPUP_EXECUTE: 'accounts:topup:execute',
  TX_LIST_VIEW: 'transactions:list:view',
  AUDIT_LIST_VIEW: 'audit:list:view',
  RBAC_ACCESS: 'rbac:access',
  RBAC_USERS_ASSIGN: 'rbac:users:assign',
  RBAC_ROLES_MANAGE: 'rbac:roles:manage',
  RISK_VIEW: 'risk:view',
  USERS_PASSWORD_RESET: 'users:password:reset',
  USERS_LOCK_EXECUTE: 'users:lock:execute',
  NOTIFICATIONS_OPS_VIEW: 'notifications:ops:view',
  // Internet Banking (customer)
  IB_HOME_VIEW: 'ib:home:view',
  IB_ACCOUNTS_VIEW: 'ib:accounts:view',
  IB_ACCOUNTS_OPEN: 'ib:accounts:open',
  IB_TRANSFER_VIEW: 'ib:transfer:view',
  IB_TRANSFER_EXECUTE: 'ib:transfer:execute',
  IB_HISTORY_VIEW: 'ib:history:view',
  IB_NOTIFICATIONS_VIEW: 'ib:notifications:view',
  IB_PROFILE_VIEW: 'ib:profile:view',
  IB_PROFILE_EDIT: 'ib:profile:edit',
  IB_PROFILE_MFA: 'ib:profile:mfa',
  IB_CARDS_VIEW: 'ib:cards:view',
  IB_WEALTH_VIEW: 'ib:wealth:view',
  IB_SUPPORT_VIEW: 'ib:support:view',
  // legacy aliases
  DASHBOARD_READ: 'dashboard:view',
  CUSTOMERS_READ: 'customers:list:view',
  CUSTOMERS_KYC: 'customers:kyc:decide',
  ACCOUNTS_READ: 'accounts:lookup:view',
  ACCOUNTS_FREEZE: 'accounts:freeze:execute',
  TX_MONITOR: 'transactions:list:view',
  AUDIT_READ: 'audit:list:view',
  RBAC_MANAGE: 'rbac:access',
  RISK_READ: 'risk:view',
} as const;

export function isStaffRole(role: string): boolean {
  const r = role?.toUpperCase?.() ?? '';
  const bare = r.startsWith('ROLE_') ? r.slice(5) : r;
  return (STAFF_ROLES as readonly string[]).includes(bare);
}

export function isStaffUser(roles: string[] | null | undefined, permissions?: string[] | null): boolean {
  if ((permissions || []).some((p) => !p.startsWith('ib:'))) return true;
  return (roles || []).some(isStaffRole);
}

export function hasPermission(
  permissions: string[] | null | undefined,
  required: string,
  roles?: string[] | null,
): boolean {
  const perms = permissions || [];
  if (perms.some((p) => p === required || p === '*')) {
    return true;
  }
  // Legacy JWT still carries rbac:manage after DB migrated to granular rbac:* codes
  if (
    required.startsWith('rbac:') &&
    perms.some((p) => p === 'rbac:manage')
  ) {
    return true;
  }
  // ADMIN / SUPER_ADMIN role → full Back Office (not IB)
  if (!required.startsWith('ib:') && (roles || []).some((r) => {
    const bare = r.toUpperCase().replace(/^ROLE_/, '');
    return bare === 'ADMIN' || bare === 'SUPER_ADMIN';
  })) {
    return true;
  }
  return false;
}

export function hasAnyPermission(
  permissions: string[] | null | undefined,
  required: string[],
  roles?: string[] | null,
): boolean {
  return required.some((r) => hasPermission(permissions, r, roles));
}
