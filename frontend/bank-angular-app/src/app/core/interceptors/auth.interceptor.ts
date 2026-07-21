import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenService } from '../services/token.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenService);
  const access = tokens.getAccessToken();
  const refresh = tokens.getRefreshToken();
  const skipAuth =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/register') ||
    req.url.includes('/auth/refresh');
  if (access && !skipAuth) {
    const headers: Record<string, string> = { Authorization: `Bearer ${access}` };
    // Lets auth-service mark which refresh session is "this device".
    if (refresh) {
      headers['X-Refresh-Token'] = refresh;
    }
    req = req.clone({ setHeaders: headers });
  }
  return next(req);
};
