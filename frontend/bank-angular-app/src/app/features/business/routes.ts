import { Routes } from '@angular/router';

export const BUSINESS_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/business-dashboard.component').then((m) => m.BusinessDashboardComponent),
  },
  {
    path: 'virtual-accounts',
    loadComponent: () =>
      import('./virtual-accounts/business-virtual-accounts.component').then(
        (m) => m.BusinessVirtualAccountsComponent
      ),
  },
  {
    path: 'collection-orders',
    loadComponent: () =>
      import('./collection-orders/business-collection-orders.component').then(
        (m) => m.BusinessCollectionOrdersComponent
      ),
  },
  {
    path: 'split-rules',
    loadComponent: () =>
      import('./split-rules/business-split-rules.component').then((m) => m.BusinessSplitRulesComponent),
  },
  {
    path: 'settlements',
    loadComponent: () =>
      import('./settlements/business-settlements.component').then((m) => m.BusinessSettlementsComponent),
  },
  {
    path: 'developer',
    loadComponent: () =>
      import('./developer/business-developer.component').then((m) => m.BusinessDeveloperComponent),
  },
  {
    path: 'members',
    loadComponent: () =>
      import('./members/business-members.component').then((m) => m.BusinessMembersComponent),
  },
  {
    path: 'open-banking',
    loadComponent: () =>
      import('../b2b-portal/b2b-portal.component').then((m) => m.B2bPortalComponent),
  },
];
