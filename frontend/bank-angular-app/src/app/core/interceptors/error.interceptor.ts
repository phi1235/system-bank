import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { resolveHttpErrorMessage } from '../utils/http-error.util';
import { CORRELATION_HEADER } from './correlation.interceptor';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // skip noisy 401 on me/bootstrap
      if (err.status === 401 && req.url.includes('/auth/me')) {
        return throwError(() => err);
      }
      // skip i18n json load failures
      if (req.url.includes('/i18n/')) {
        return throwError(() => err);
      }

      const toast = injector.get(ToastService);
      const i18n = injector.get(TranslateService);

      // User-facing toast: plain language only — no HTTP status, no corr ref, no raw codes.
      const msg = resolveHttpErrorMessage(err, i18n);

      // Dev/debug: keep correlation id in console (also in BE logs / Network headers).
      const body = err.error;
      const correlationId =
        body?.meta?.correlationId ||
        err.headers?.get(CORRELATION_HEADER) ||
        req.headers.get(CORRELATION_HEADER) ||
        null;
      if (correlationId || err.status >= 500) {
        // eslint-disable-next-line no-console
        console.warn('[api-error]', {
          url: req.url,
          method: req.method,
          status: err.status,
          code: body?.error?.code,
          correlationId,
          message: body?.error?.message || err.message,
        });
      }

      // don't toast every 401 during redirect
      if (err.status !== 401) {
        toast.error(msg);
      }
      return throwError(() => err);
    }),
  );
};
