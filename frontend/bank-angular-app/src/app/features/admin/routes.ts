import { Routes } from '@angular/router';
import { AdminShellComponent } from '../../layouts/admin-shell/admin-shell.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./dashboard/dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'customers',
        loadComponent: () => import('./customers/customers.component').then((m) => m.AdminCustomersComponent),
      },
      {
        path: 'accounts',
        loadComponent: () => import('./accounts/accounts.component').then((m) => m.AdminAccountsComponent),
      },
      {
        path: 'transactions',
        loadComponent: () => import('./transfers/transfers.component').then((m) => m.AdminTransfersComponent),
      },
      {
        path: 'audit',
        loadComponent: () => import('./audit/audit.component').then((m) => m.AdminAuditComponent),
      },
      {
        path: 'rbac',
        loadComponent: () => import('./rbac/rbac.component').then((m) => m.AdminRbacComponent),
      },
      {
        path: 'risk',
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.AdminPlaceholderComponent),
        data: { titleKey: 'ADMIN.RISK_TITLE', subtitleKey: 'ADMIN.RISK_SUB' },
      },
    ],
  },
];
