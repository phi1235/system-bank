import { Routes } from '@angular/router';
import { permissionAnyGuard, permissionGuard } from '../../core/guards/role.guard';
import { PERMISSIONS } from '../../core/services/rbac.util';
import { AdminShellComponent } from '../../layouts/admin-shell/admin-shell.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [permissionGuard([PERMISSIONS.DASHBOARD_VIEW])],
        loadComponent: () => import('./dashboard/dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'customers',
        canActivate: [permissionGuard([PERMISSIONS.CUSTOMERS_LIST_VIEW])],
        loadComponent: () => import('./customers/customers.component').then((m) => m.AdminCustomersComponent),
      },
      {
        path: 'support-tickets',
        canActivate: [permissionGuard([PERMISSIONS.SUPPORT_TICKETS_LIST])],
        loadComponent: () =>
          import('./support-tickets/support-tickets.component').then((m) => m.AdminSupportTicketsComponent),
      },
      {
        path: 'accounts',
        canActivate: [permissionGuard([PERMISSIONS.ACCOUNTS_LOOKUP_VIEW])],
        loadComponent: () => import('./accounts/accounts.component').then((m) => m.AdminAccountsComponent),
      },
      {
        path: 'cards',
        canActivate: [permissionGuard([PERMISSIONS.ACCOUNTS_LOOKUP_VIEW])],
        loadComponent: () =>
          import('./cards/card-approvals.component').then((m) => m.AdminCardApprovalsComponent),
      },
      {
        path: 'deposits',
        canActivate: [permissionGuard([PERMISSIONS.DEPOSITS_SUMMARY_VIEW])],
        loadComponent: () => import('./deposits/deposits.component').then((m) => m.AdminDepositsComponent),
      },
      {
        path: 'deposits/contracts',
        canActivate: [permissionGuard([PERMISSIONS.DEPOSITS_SUMMARY_VIEW])],
        loadComponent: () =>
          import('./deposits/deposit-contracts.component').then((m) => m.AdminDepositContractsComponent),
      },
      {
        path: 'transactions',
        canActivate: [permissionGuard([PERMISSIONS.TX_LIST_VIEW])],
        loadComponent: () => import('./transfers/transfers.component').then((m) => m.AdminTransfersComponent),
      },
      {
        path: 'transactions/report',
        canActivate: [permissionGuard([PERMISSIONS.TX_REPORT_VIEW])],
        loadComponent: () =>
          import('./transaction-report/transaction-report.component').then((m) => m.AdminTransactionReportComponent),
      },
      {
        path: 'reconciliation',
        canActivate: [permissionGuard([PERMISSIONS.TX_RECON_VIEW])],
        loadComponent: () =>
          import('./reconciliation/reconciliation.component').then((m) => m.AdminReconciliationComponent),
      },
      {
        path: 'forensics',
        canActivate: [permissionGuard([PERMISSIONS.FORENSICS_VIEW])],
        loadComponent: () =>
          import('./forensics/forensics.component').then((m) => m.AdminForensicsComponent),
      },
      {
        path: 'outbox',
        canActivate: [permissionGuard([PERMISSIONS.TX_LIST_VIEW])],
        loadComponent: () => import('./outbox/outbox.component').then((m) => m.AdminOutboxComponent),
      },
      {
        path: 'audit',
        canActivate: [permissionGuard([PERMISSIONS.AUDIT_LIST_VIEW])],
        loadComponent: () => import('./audit/audit.component').then((m) => m.AdminAuditComponent),
      },
      {
        path: 'rbac',
        canActivate: [permissionGuard([PERMISSIONS.RBAC_ACCESS])],
        loadComponent: () => import('./rbac/rbac.component').then((m) => m.AdminRbacComponent),
      },
      {
        path: 'risk',
        canActivate: [permissionGuard([PERMISSIONS.RISK_VIEW])],
        loadComponent: () => import('./risk/risk.component').then((m) => m.AdminRiskComponent),
      },
      {
        path: 'users',
        canActivate: [
          permissionAnyGuard([
            PERMISSIONS.USERS_PASSWORD_RESET,
            PERMISSIONS.USERS_LOCK_EXECUTE,
            PERMISSIONS.RBAC_USERS_ASSIGN,
            PERMISSIONS.RBAC_ACCESS,
          ]),
        ],
        loadComponent: () => import('./users/users.component').then((m) => m.AdminUsersComponent),
      },
      {
        path: 'virtual-accounts',
        canActivate: [permissionGuard([PERMISSIONS.VA_OPERATIONS_VIEW])],
        loadComponent: () =>
          import('./virtual-accounts/admin-virtual-accounts.component').then((m) => m.AdminVirtualAccountsComponent),
      },
      {
        path: 'settlements',
        canActivate: [permissionGuard([PERMISSIONS.SETTLEMENT_VIEW])],
        loadComponent: () =>
          import('./settlements/admin-settlements.component').then((m) => m.AdminSettlementsComponent),
      },
      {
        path: 'notifications/sandbox',
        loadComponent: () => import('./notification-sandbox/notification-sandbox.component').then((m) => m.NotificationSandboxComponent),
      },
    ],
  },
];
