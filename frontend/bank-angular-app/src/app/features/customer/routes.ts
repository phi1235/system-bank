import { Routes } from '@angular/router';
import { CustomerShellComponent } from '../../layouts/customer-shell/customer-shell.component';

export const CUSTOMER_ROUTES: Routes = [
  {
    path: '',
    component: CustomerShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      { path: 'home', loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent) },
      { path: 'accounts', loadComponent: () => import('./accounts/accounts.component').then((m) => m.AccountsComponent) },
      {
        path: 'payments/transfer',
        loadComponent: () => import('./transfer/transfer.component').then((m) => m.TransferComponent),
      },
      { path: 'history', loadComponent: () => import('./history/history.component').then((m) => m.HistoryComponent) },
      { path: 'profile', loadComponent: () => import('./profile/profile.component').then((m) => m.ProfileComponent) },
      {
        path: 'cards',
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
        data: { titleKey: 'NAV.CARDS', subtitleKey: 'CUSTOMER.CARDS_SUB' },
      },
      {
        path: 'wealth',
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
        data: { titleKey: 'NAV.WEALTH', subtitleKey: 'CUSTOMER.WEALTH_SUB' },
      },
      {
        path: 'support',
        loadComponent: () => import('./placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
        data: { titleKey: 'NAV.SUPPORT', subtitleKey: 'CUSTOMER.SUPPORT_SUB' },
      },
    ],
  },
];
