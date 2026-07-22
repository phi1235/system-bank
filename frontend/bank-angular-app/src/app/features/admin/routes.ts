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
        path: 'accounts',
        canActivate: [permissionGuard([PERMISSIONS.ACCOUNTS_LOOKUP_VIEW])],
        loadComponent: () => import('./accounts/accounts.component').then((m) => m.AdminAccountsComponent),
      },
      {
        path: 'transactions',
        canActivate: [permissionGuard([PERMISSIONS.TX_LIST_VIEW])],
        loadComponent: () => import('./transfers/transfers.component').then((m) => m.AdminTransfersComponent),
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
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.AdminPlaceholderComponent),
        data: { titleKey: 'ADMIN.RISK_TITLE', subtitleKey: 'ADMIN.RISK_SUB' },
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
        path: 'notifications/sandbox',
        loadComponent: () => import('./notification-sandbox/notification-sandbox.component').then((m) => m.NotificationSandboxComponent),
      },
    ],
  },
];
