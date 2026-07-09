import { Routes } from '@angular/router';
import { guestGuard } from '../../core/guards/guest.guard';

export const CUSTOMER_AUTH_ROUTES: Routes = [
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./login/login.component').then(m => m.LoginComponent) },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./register/register.component').then(m => m.RegisterComponent) },
  { path: 'mfa', loadComponent: () => import('./mfa/mfa.component').then(m => m.MfaComponent) },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
];
