import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthApiService } from '../services/auth-api.service';
import { TokenService } from '../services/token.service';

let refreshing = false;

export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenService);
  const router = inject(Router);
  const injector = inject(Injector);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || req.url.includes('/auth/login') || req.url.includes('/auth/refresh')) {
        return throwError(() => err);
      }
      const refresh = tokens.getRefreshToken();
      if (!refresh || refreshing) {
        tokens.clear();
        router.navigateByUrl('/auth/login');
        return throwError(() => err);
      }
      refreshing = true;
      const authApi = injector.get(AuthApiService);
      return authApi.refresh(refresh).pipe(
        switchMap((t) => {
          tokens.setTokens(t.accessToken, t.refreshToken);
          refreshing = false;
          return next(
            req.clone({ setHeaders: { Authorization: `Bearer ${t.accessToken}` } }),
          );
        }),
        catchError((e) => {
          refreshing = false;
          tokens.clear();
          router.navigateByUrl('/auth/login');
          return throwError(() => e);
        }),
      );
    }),
  );
};

