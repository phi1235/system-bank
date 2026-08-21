import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { BusinessContextService } from '../services/business-context.service';

export function businessPermissionGuard(requiredPerms: string[]): CanActivateFn {
  return () => {
    const businessContext = inject(BusinessContextService);
    const router = inject(Router);

    return businessContext.ensureLoaded().pipe(
      map(() => {
        if (businessContext.hasAnyPermission(requiredPerms)) {
          return true;
        }
        return router.createUrlTree(['/business/dashboard']);
      })
    );
  };
}
