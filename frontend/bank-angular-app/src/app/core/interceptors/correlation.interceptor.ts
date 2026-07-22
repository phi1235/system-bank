import { HttpInterceptorFn } from '@angular/common/http';

/** Header name aligned with gateway + common-lib SecurityHeaders.CORRELATION_ID. */
export const CORRELATION_HEADER = 'X-Correlation-Id';

/**
 * Attaches a per-request correlation id so gateway/services can link logs to the FE call.
 * Browser crypto.randomUUID is available on modern Chromium / Firefox / Safari.
 */
export const correlationInterceptor: HttpInterceptorFn = (req, next) => {
  // Skip static assets / i18n — no need to correlate translation loads.
  if (req.url.includes('/i18n/') || req.url.endsWith('.json') && !req.url.includes('/api/')) {
    return next(req);
  }
  if (req.headers.has(CORRELATION_HEADER)) {
    return next(req);
  }
  const id =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `fe-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return next(req.clone({ setHeaders: { [CORRELATION_HEADER]: id } }));
};
