import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/guards/role.guard';
import { PERMISSIONS } from '../../core/services/rbac.util';
import { CustomerShellComponent } from '../../layouts/customer-shell/customer-shell.component';

export const CUSTOMER_ROUTES: Routes = [
  {
    path: '',
    component: CustomerShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        canActivate: [permissionGuard([PERMISSIONS.IB_HOME_VIEW])],
        loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'accounts',
        canActivate: [permissionGuard([PERMISSIONS.IB_ACCOUNTS_VIEW])],
        loadComponent: () => import('./accounts/accounts.component').then((m) => m.AccountsComponent),
      },
      {
        path: 'accounts/:id/statement',
        canActivate: [permissionGuard([PERMISSIONS.IB_ACCOUNTS_VIEW])],
        loadComponent: () => import('./statement/statement.component').then((m) => m.StatementComponent),
      },
      {
        path: 'payments/transfer',
        canActivate: [permissionGuard([PERMISSIONS.IB_TRANSFER_VIEW])],
        loadComponent: () => import('./transfer/transfer.component').then((m) => m.TransferComponent),
      },
      {
        path: 'payments/beneficiaries',
        canActivate: [permissionGuard([PERMISSIONS.IB_TRANSFER_VIEW])],
        loadComponent: () =>
          import('./beneficiaries/beneficiaries.component').then((m) => m.BeneficiariesComponent),
      },
      {
        path: 'history',
        canActivate: [permissionGuard([PERMISSIONS.IB_HISTORY_VIEW])],
        loadComponent: () => import('./history/history.component').then((m) => m.HistoryComponent),
      },
      {
        path: 'notifications',
        canActivate: [permissionGuard([PERMISSIONS.IB_NOTIFICATIONS_VIEW])],
        loadComponent: () =>
          import('./notifications/notifications.component').then(
            (m) => m.CustomerNotificationsComponent,
          ),
      },
      {
        path: 'profile',
        canActivate: [permissionGuard([PERMISSIONS.IB_PROFILE_VIEW])],
        loadComponent: () => import('./profile/profile.component').then((m) => m.ProfileComponent),
      },
      {
        path: 'cards',
        canActivate: [permissionGuard([PERMISSIONS.IB_CARDS_VIEW])],
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
        data: { titleKey: 'NAV.CARDS', subtitleKey: 'CUSTOMER.CARDS_SUB' },
      },
      {
        path: 'wealth',
        canActivate: [permissionGuard([PERMISSIONS.IB_WEALTH_VIEW])],
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
        data: { titleKey: 'NAV.WEALTH', subtitleKey: 'CUSTOMER.WEALTH_SUB' },
      },
      {
        path: 'support',
        canActivate: [permissionGuard([PERMISSIONS.IB_SUPPORT_VIEW])],
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
        data: { titleKey: 'NAV.SUPPORT', subtitleKey: 'CUSTOMER.SUPPORT_SUB' },
      },
    ],
  },
];
