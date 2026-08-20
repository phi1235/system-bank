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
  CUSTOMERS_KYC_REVIEW: 'customers:kyc:review',
  CUSTOMERS_KYC_APPROVE: 'customers:kyc:approve',
  ACCOUNTS_LOOKUP_VIEW: 'accounts:lookup:view',
  ACCOUNTS_FREEZE_EXECUTE: 'accounts:freeze:execute',
  ACCOUNTS_TOPUP_EXECUTE: 'accounts:topup:execute',
  CARDS_APPROVE_EXECUTE: 'cards:approve:execute',
  DEPOSITS_SUMMARY_VIEW: 'deposits:summary:view',
  DEPOSITS_BATCH_EXECUTE: 'deposits:batch:execute',
  DEPOSITS_PRODUCTS_MANAGE: 'deposits:products:manage',
  TX_LIST_VIEW: 'transactions:list:view',
  TX_REPORT_VIEW: 'transactions:report:view',
  TX_RECON_VIEW: 'transactions:recon:view',
  TX_RECON_EXECUTE: 'transactions:recon:execute',
  FORENSICS_VIEW: 'forensics:view',
  FORENSICS_VERIFY_EXECUTE: 'forensics:verify:execute',
  FORENSICS_CASE_REVIEW: 'forensics:case:review',
  FORENSICS_EVIDENCE_EXPORT: 'forensics:evidence:export',
  FORENSICS_REPLAY_EXECUTE: 'forensics:replay:execute',
  FORENSICS_COPILOT_USE: 'forensics:copilot:use',
  FORENSICS_AUDIT_VIEW: 'forensics:audit:view',
  FORENSICS_ADMIN: 'forensics:admin',
  AUDIT_LIST_VIEW: 'audit:list:view',
  RBAC_ACCESS: 'rbac:access',
  RBAC_USERS_ASSIGN: 'rbac:users:assign',
  RBAC_ROLES_MANAGE: 'rbac:roles:manage',
  RISK_VIEW: 'risk:view',
  RISK_MANAGE: 'risk:manage',
  RISK_DECIDE: 'risk:decide',
  USERS_PASSWORD_RESET: 'users:password:reset',
  USERS_LOCK_EXECUTE: 'users:lock:execute',
  NOTIFICATIONS_OPS_VIEW: 'notifications:ops:view',
  SUPPORT_TICKETS_LIST: 'support:tickets:list',
  SUPPORT_TICKETS_CLAIM: 'support:tickets:claim',
  SUPPORT_TICKETS_DECIDE: 'support:tickets:decide',
  // Back Office VA & Settlement Operations
  VA_OPERATIONS_VIEW: 'va:operations:view',
  VA_OPERATIONS_MANAGE: 'va:operations:manage',
  SETTLEMENT_VIEW: 'settlement:view',
  SETTLEMENT_APPROVE: 'settlement:approve',
  SETTLEMENT_RETRY: 'settlement:retry',
  PAYOUT_VIEW: 'payout:view',
  PAYOUT_EXECUTE: 'payout:execute',
  // B2B Business Portal
  BUSINESS_DASHBOARD_VIEW: 'business:dashboard:view',
  BUSINESS_VA_VIEW: 'business:va:view',
  BUSINESS_VA_MANAGE: 'business:va:manage',
  BUSINESS_ORDERS_VIEW: 'business:orders:view',
  BUSINESS_ORDERS_MANAGE: 'business:orders:manage',
  BUSINESS_SETTLEMENTS_VIEW: 'business:settlements:view',
  BUSINESS_SETTLEMENTS_EXECUTE: 'business:settlements:execute',
  BUSINESS_CREDENTIALS_MANAGE: 'business:credentials:manage',
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
  IB_BILLS_VIEW: 'ib:bills:view',
  IB_BILLS_EXECUTE: 'ib:bills:execute',
  IB_WEALTH_VIEW: 'ib:wealth:view',
  IB_SUPPORT_VIEW: 'ib:support:view',
  IB_SUPPORT_CREATE: 'ib:support:create',
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
