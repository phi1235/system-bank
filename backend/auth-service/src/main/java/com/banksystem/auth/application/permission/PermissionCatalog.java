package com.banksystem.auth.application.permission;

import com.banksystem.common.security.SecurityHeaders;
import java.util.List;

public final class PermissionCatalog {

  private PermissionCatalog() {}

  public static final List<String> ALL_PERMISSIONS = List.of(
      SecurityHeaders.PERM_DASHBOARD_VIEW,
      SecurityHeaders.PERM_CUSTOMERS_LIST_VIEW,
      SecurityHeaders.PERM_CUSTOMERS_KYC_DECIDE,
      SecurityHeaders.PERM_ACCOUNTS_LOOKUP_VIEW,
      SecurityHeaders.PERM_ACCOUNTS_FREEZE_EXECUTE,
      SecurityHeaders.PERM_TX_LIST_VIEW,
      SecurityHeaders.PERM_AUDIT_LIST_VIEW,
      SecurityHeaders.PERM_RBAC_ACCESS,
      SecurityHeaders.PERM_RBAC_USERS_ASSIGN,
      SecurityHeaders.PERM_RBAC_ROLES_MANAGE,
      SecurityHeaders.PERM_RISK_VIEW,
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
      SecurityHeaders.PERM_IB_SUPPORT_VIEW
  );
}
