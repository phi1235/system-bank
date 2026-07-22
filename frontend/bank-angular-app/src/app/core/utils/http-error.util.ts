import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

/**
 * Resolve a user-facing error message from an HTTP failure.
 * Never surfaces raw "500: OK" / statusText junk.
 */
export function resolveHttpErrorMessage(
  err: HttpErrorResponse,
  i18n: TranslateService,
): string {
  const body = err?.error;
  const code = (body?.error?.code || body?.code) as string | undefined;

  if (code) {
    const key = `ERRORS.${code}`;
    const localized = i18n.instant(key);
    if (localized && localized !== key) {
      return localized;
    }
    // Known business message without dedicated i18n key — still better than HTTP junk
    if (body?.error?.message && !looksLikeHttpJunk(body.error.message)) {
      return String(body.error.message);
    }
  }

  if (body?.error?.message && !looksLikeHttpJunk(body.error.message)) {
    let msg = String(body.error.message);
    if (Array.isArray(body.error.details) && body.error.details.length) {
      msg += ': ' + body.error.details.join(', ');
    }
    return msg;
  }

  if (body?.message && !looksLikeHttpJunk(body.message)) {
    return String(body.message);
  }

  // Network / offline (status 0)
  if (err.status === 0) {
    return i18n.instant('ERRORS.NETWORK');
  }

  return statusFallbackMessage(err.status, i18n);
}

function statusFallbackMessage(status: number, i18n: TranslateService): string {
  if (status === 400 || status === 422) {
    return i18n.instant('ERRORS.BAD_REQUEST');
  }
  if (status === 401) {
    return i18n.instant('ERRORS.UNAUTHORIZED');
  }
  if (status === 403) {
    return i18n.instant('ERRORS.FORBIDDEN');
  }
  if (status === 404) {
    return i18n.instant('ERRORS.NOT_FOUND');
  }
  if (status === 408 || status === 504) {
    return i18n.instant('ERRORS.TIMEOUT');
  }
  if (status === 409) {
    return i18n.instant('ERRORS.CONFLICT');
  }
  if (status === 429) {
    return i18n.instant('ERRORS.TOO_MANY_REQUESTS');
  }
  if (status >= 500) {
    return i18n.instant('ERRORS.SERVER');
  }
  return i18n.instant('ERRORS.GENERIC');
}

/** True for messages like "500: OK", "Http failure response for ...: 500 OK". */
function looksLikeHttpJunk(msg: unknown): boolean {
  if (msg == null) {
    return true;
  }
  const s = String(msg).trim();
  if (!s) {
    return true;
  }
  if (/^\d{3}\s*:\s*(OK|Error|Unknown)?$/i.test(s)) {
    return true;
  }
  if (/^Http failure response/i.test(s)) {
    return true;
  }
  if (/^\d{3}\s+OK$/i.test(s)) {
    return true;
  }
  return false;
}
