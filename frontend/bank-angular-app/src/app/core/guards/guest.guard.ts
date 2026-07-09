import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { map, take } from 'rxjs';
import { selectRoles } from '../../store/auth/auth.selectors';
import { TokenService } from '../services/token.service';

export const guestGuard: CanActivateFn = () => {
  const tokens = inject(TokenService);
  const store = inject(Store);
  const router = inject(Router);
  if (!tokens.hasToken()) return true;
  return store.select(selectRoles).pipe(
    take(1),
    map((roles) => {
      if (roles.includes('ADMIN')) return router.createUrlTree(['/admin']);
      if (roles.includes('CUSTOMER')) return router.createUrlTree(['/customer/home']);
      return true;
    }),
  );
};
