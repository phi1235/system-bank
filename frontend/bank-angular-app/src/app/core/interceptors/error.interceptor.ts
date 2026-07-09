import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const i18n = inject(TranslateService);
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
      const body = err.error;
      let msg = i18n.instant('ERRORS.GENERIC');
      if (body?.error?.message) {
        msg = body.error.message;
        if (body.error.details?.length) {
          msg += ': ' + body.error.details.join(', ');
        }
      } else if (body?.message) {
        msg = body.message;
      } else if (err.message) {
        msg = err.status ? `${err.status}: ${err.statusText || err.message}` : err.message;
      }
      // don't toast every 401 during redirect
      if (err.status !== 401) {
        toast.error(msg);
      }
      return throwError(() => err);
    }),
  );
};
