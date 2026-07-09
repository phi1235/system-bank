import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { filter, map, take, timeout, catchError, of } from 'rxjs';
import { selectAuthLoading, selectRoles } from '../../store/auth/auth.selectors';
import { TokenService } from '../services/token.service';
import { rolesFromToken } from '../services/jwt.util';

export function roleGuard(allowed: string[]): CanActivateFn {
  return () => {
    const store = inject(Store);
    const router = inject(Router);
    const tokens = inject(TokenService);

    const tokenRoles = rolesFromToken(tokens.getAccessToken());
    if (allowed.some((r) => tokenRoles.includes(r))) {
      return true;
    }

    return store.select(selectAuthLoading).pipe(
      filter((loading) => !loading),
      take(1),
      timeout(5000),
      catchError(() => of(false)),
      map(() => {
        // re-read after bootstrap
        const fromJwt = rolesFromToken(tokens.getAccessToken());
        let roles = fromJwt;
        // also peek store synchronously via subscription is awkward; use jwt primarily
        if (!roles.length) {
          return router.createUrlTree(['/auth/login']);
        }
        const ok = allowed.some((r) => roles.includes(r));
        if (ok) return true;
        if (roles.includes('ADMIN')) return router.createUrlTree(['/admin']);
        if (roles.includes('CUSTOMER')) return router.createUrlTree(['/customer/home']);
        return router.createUrlTree(['/auth/login']);
      }),
    );
  };
}
