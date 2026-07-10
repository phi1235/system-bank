import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { catchError, filter, map, of, take, timeout } from 'rxjs';
import { selectAuthLoading, selectPermissions, selectRoles } from '../../store/auth/auth.selectors';
import { TokenService } from '../services/token.service';
import { permissionsFromToken, rolesFromToken } from '../services/jwt.util';
import { isStaffUser } from '../services/rbac.util';

export function roleGuard(allowed: string[]): CanActivateFn {
  return () => {
    const store = inject(Store);
    const router = inject(Router);
    const tokens = inject(TokenService);

    const tokenRoles = rolesFromToken(tokens.getAccessToken());
    const tokenPerms = permissionsFromToken(tokens.getAccessToken());
    if (matchesAllowed(tokenRoles, tokenPerms, allowed)) {
      return true;
    }

    return store.select(selectAuthLoading).pipe(
      filter((loading) => !loading),
      take(1),
      timeout(5000),
      catchError(() => of(false)),
      map(() => {
        const roles = rolesFromToken(tokens.getAccessToken());
        const perms = permissionsFromToken(tokens.getAccessToken());
        if (!roles.length && !perms.length) {
          return router.createUrlTree(['/auth/login']);
        }
        if (matchesAllowed(roles, perms, allowed)) {
          return true;
        }
        if (isStaffUser(roles, perms)) {
          return router.createUrlTree(['/admin']);
        }
        if (roles.includes('CUSTOMER')) {
          return router.createUrlTree(['/customer/home']);
        }
        return router.createUrlTree(['/auth/login']);
      }),
    );
  };
}

/** Route data: requiredPermissions: string[] — all required (AND). */
export function permissionGuard(required: string[]): CanActivateFn {
  return () => {
    const router = inject(Router);
    const tokens = inject(TokenService);
    const store = inject(Store);

    const check = (roles: string[], perms: string[]) =>
      required.every((p) => {
        if (roles.some((r) => ['ADMIN', 'SUPER_ADMIN'].includes(r.toUpperCase().replace(/^ROLE_/, '')))) {
          return true;
        }
        return perms.includes(p) || perms.includes('*');
      });

    const roles = rolesFromToken(tokens.getAccessToken());
    const perms = permissionsFromToken(tokens.getAccessToken());
    if (check(roles, perms)) {
      return true;
    }

    return store.select(selectRoles).pipe(
      take(1),
      map((storeRoles) => {
        // re-read jwt after bootstrap
        const r = rolesFromToken(tokens.getAccessToken()) || storeRoles;
        const p = permissionsFromToken(tokens.getAccessToken());
        if (check(r, p)) return true;
        return router.createUrlTree(['/admin']);
      }),
    );
  };
}

function matchesAllowed(roles: string[], permissions: string[], allowed: string[]): boolean {
  if (allowed.includes('*STAFF*') || allowed.includes('STAFF')) {
    return isStaffUser(roles, permissions);
  }
  return allowed.some((a) => roles.includes(a) || roles.includes('ROLE_' + a));
}
