package com.banksystem.auth.application.rbac;

import com.banksystem.common.security.SecurityHeaders;
import java.util.List;

public final class PermissionCatalog {

  private PermissionCatalog() {}

  public static final List<String> ALL_PERMISSIONS = List.of(
      SecurityHeaders.PERM_DASHBOARD_VIEW,
      SecurityHeaders.PERM_CUSTOMERS_LIST_VIEW,
      SecurityHeaders.PERM_CUSTOMERS_KYC_DECIDE,
      SecurityHeaders.PERM_CUSTOMERS_KYC_REVIEW,
      SecurityHeaders.PERM_CUSTOMERS_KYC_APPROVE,
      SecurityHeaders.PERM_ACCOUNTS_LOOKUP_VIEW,
      SecurityHeaders.PERM_ACCOUNTS_FREEZE_EXECUTE,
      SecurityHeaders.PERM_TX_LIST_VIEW,
      SecurityHeaders.PERM_AUDIT_LIST_VIEW,
      SecurityHeaders.PERM_RBAC_ACCESS,
      SecurityHeaders.PERM_RBAC_USERS_ASSIGN,
      SecurityHeaders.PERM_RBAC_ROLES_MANAGE,
      SecurityHeaders.PERM_RISK_VIEW,
      SecurityHeaders.PERM_RISK_MANAGE,
      SecurityHeaders.PERM_RISK_DECIDE,
      SecurityHeaders.PERM_FORENSICS_VIEW,
      SecurityHeaders.PERM_FORENSICS_VERIFY_EXECUTE,
      SecurityHeaders.PERM_FORENSICS_CASE_REVIEW,
      SecurityHeaders.PERM_FORENSICS_EVIDENCE_EXPORT,
      SecurityHeaders.PERM_FORENSICS_REPLAY_EXECUTE,
      SecurityHeaders.PERM_FORENSICS_COPILOT_USE,
      SecurityHeaders.PERM_FORENSICS_AUDIT_VIEW,
      SecurityHeaders.PERM_FORENSICS_ADMIN,
      SecurityHeaders.PERM_IB_HOME_VIEW,
      SecurityHeaders.PERM_IB_ACCOUNTS_VIEW,
      SecurityHeaders.PERM_IB_ACCOUNTS_OPEN,
      SecurityHeaders.PERM_IB_TRANSFER_VIEW,
      SecurityHeaders.PERM_IB_TRANSFER_EXECUTE,
      SecurityHeaders.PERM_IB_HISTORY_VIEW,
      SecurityHeaders.PERM_IB_PROFILE_VIEW,
      SecurityHeaders.PERM_IB_PROFILE_EDIT,
      SecurityHeaders.PERM_IB_PROFILE_MFA,
      SecurityHeaders.PERM_IB_CARDS_VIEW,
      SecurityHeaders.PERM_IB_WEALTH_VIEW,
      SecurityHeaders.PERM_IB_SUPPORT_VIEW,
      SecurityHeaders.PERM_IB_NOTIFICATIONS_VIEW,
      SecurityHeaders.PERM_VA_OPERATIONS_VIEW,
      SecurityHeaders.PERM_VA_OPERATIONS_REVIEW,
      SecurityHeaders.PERM_SETTLEMENT_VIEW,
      SecurityHeaders.PERM_SETTLEMENT_RETRY,
      SecurityHeaders.PERM_SETTLEMENT_APPROVE,
      SecurityHeaders.PERM_PAYOUT_VIEW,
      SecurityHeaders.PERM_PAYOUT_APPROVE,
      SecurityHeaders.PERM_BUSINESS_DASHBOARD_VIEW,
      SecurityHeaders.PERM_BUSINESS_VA_VIEW,
      SecurityHeaders.PERM_BUSINESS_VA_MANAGE,
      SecurityHeaders.PERM_BUSINESS_ORDERS_VIEW,
      SecurityHeaders.PERM_BUSINESS_ORDERS_MANAGE,
      SecurityHeaders.PERM_BUSINESS_SETTLEMENTS_VIEW,
      SecurityHeaders.PERM_BUSINESS_SETTLEMENTS_EXECUTE,
      SecurityHeaders.PERM_BUSINESS_SPLIT_VIEW,
      SecurityHeaders.PERM_BUSINESS_SPLIT_MANAGE,
      SecurityHeaders.PERM_BUSINESS_CREDENTIALS_MANAGE
  );
}
