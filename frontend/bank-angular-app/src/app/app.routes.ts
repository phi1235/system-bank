import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'auth/login' },

  {
    path: 'auth',
    loadChildren: () =>
      import('./features/customer-auth/routes').then((m) => m.CUSTOMER_AUTH_ROUTES),
  },

  {
    path: 'admin/login',
    loadComponent: () =>
      import('./features/admin-auth/login/admin-login.component').then((m) => m.AdminLoginComponent),
  },

  {
    path: 'customer',
    canActivate: [authGuard, roleGuard(['CUSTOMER', 'ADMIN', 'SUPER_ADMIN'])],
    loadChildren: () => import('./features/customer/routes').then((m) => m.CUSTOMER_ROUTES),
  },

  {
    path: 'admin',
    canActivate: [authGuard, roleGuard(['STAFF'])],
    loadChildren: () => import('./features/admin/routes').then((m) => m.ADMIN_ROUTES),
  },

  {
    path: 'corporate',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/corporate/corporate.routes').then((m) => m.CORPORATE_ROUTES),
  },

  // ── Dev Sandbox — any authenticated user (customer OR staff), no role restriction ──
  {
    path: 'dev/sandbox',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dev-sandbox/dev-sandbox.component').then((m) => m.DevSandboxComponent),
  },

  { path: '**', redirectTo: 'auth/login' },
];
