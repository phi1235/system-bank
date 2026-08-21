import { Routes } from '@angular/router';
import { businessPermissionGuard } from '../../core/guards/business-permission.guard';

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
    canActivate: [businessPermissionGuard(['va:view', 'va:manage', 'va:create'])],
    loadComponent: () =>
      import('./virtual-accounts/business-virtual-accounts.component').then(
        (m) => m.BusinessVirtualAccountsComponent
      ),
  },
  {
    path: 'collection-orders',
    canActivate: [businessPermissionGuard(['collection:view', 'collection:create', 'collection:edit'])],
    loadComponent: () =>
      import('./collection-orders/business-collection-orders.component').then(
        (m) => m.BusinessCollectionOrdersComponent
      ),
  },
  {
    path: 'split-rules',
    canActivate: [businessPermissionGuard(['split:view', 'split:manage', 'split:create'])],
    loadComponent: () =>
      import('./split-rules/business-split-rules.component').then((m) => m.BusinessSplitRulesComponent),
  },
  {
    path: 'settlements',
    canActivate: [businessPermissionGuard(['transfer:view', 'transfer:create', 'transfer:approve', 'batch:create', 'batch:approve'])],
    loadComponent: () =>
      import('./settlements/business-settlements.component').then((m) => m.BusinessSettlementsComponent),
  },
  {
    path: 'developer',
    canActivate: [businessPermissionGuard(['developer:view', 'developer:manage', 'developer:create'])],
    loadComponent: () =>
      import('./developer/business-developer.component').then((m) => m.BusinessDeveloperComponent),
  },
  {
    path: 'members',
    canActivate: [businessPermissionGuard(['org:members', 'org:members:view', 'org:members:manage'])],
    loadComponent: () =>
      import('./members/business-members.component').then((m) => m.BusinessMembersComponent),
  },
  {
    path: 'roles',
    canActivate: [businessPermissionGuard(['org:roles', 'org:roles:view', 'org:roles:manage'])],
    loadComponent: () =>
      import('./roles/business-roles.component').then((m) => m.BusinessRolesComponent),
  },
  {
    path: 'open-banking',
    canActivate: [businessPermissionGuard(['openbanking:view', 'openbanking:manage', 'openbanking:create'])],
    loadComponent: () =>
      import('../b2b-portal/b2b-portal.component').then((m) => m.B2bPortalComponent),
  },
];
