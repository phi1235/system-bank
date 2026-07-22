import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
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

      const body = err.error;
      let msg = i18n.instant('ERRORS.GENERIC');
      const code = body?.error?.code as string | undefined;
      if (code) {
        const key = `ERRORS.${code}`;
        const localized = i18n.instant(key);
        if (localized && localized !== key) {
          msg = localized;
        } else if (body?.error?.message) {
          msg = body.error.message;
        }
      } else if (body?.error?.message) {
        msg = body.error.message;
        if (body.error.details?.length) {
          msg += ': ' + body.error.details.join(', ');
        }
      } else if (body?.message) {
        msg = body.message;
      } else if (err.message) {
        msg = err.status ? `${err.status}: ${err.statusText || err.message}` : err.message;
      }

      const correlationId =
        body?.meta?.correlationId ||
        err.headers?.get(CORRELATION_HEADER) ||
        req.headers.get(CORRELATION_HEADER) ||
        null;
      if (correlationId) {
        msg = i18n.instant('ERRORS.WITH_REF', { message: msg, ref: correlationId });
      }

      // don't toast every 401 during redirect
      if (err.status !== 401) {
        toast.error(msg);
      }
      return throwError(() => err);
    }),
  );
};
