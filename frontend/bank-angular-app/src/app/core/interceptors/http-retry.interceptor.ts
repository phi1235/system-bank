import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { retry, timer } from 'rxjs';

/**
 * Automatically retries GET requests 1-2 times with exponential delay
 * when facing transient network errors (status 0, 502, 503, 504).
 */
export const httpRetryInterceptor: HttpInterceptorFn = (req, next) => {
  // Only retry idempotent GET requests
  if (req.method !== 'GET') {
    return next(req);
  }

  return next(req).pipe(
    retry({
      count: 2,
      delay: (error: HttpErrorResponse, retryCount: number) => {
        // Only retry network failures or server temporary unavailable
        if (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504) {
          return timer(retryCount * 500);
        }
        throw error;
      },
    }),
  );
};
