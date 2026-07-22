import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

/**
 * User-facing error text only.
 * - Prefer i18n for known business codes (never show the raw code).
 * - Never surface HTTP junk like "500: OK" / statusText / corr refs.
 * - Correlation ids stay in Network/console/BE logs for support — not toasts.
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
    // Business message without dedicated key — show human text, never the bare code
    if (body?.error?.message && isHumanMessage(body.error.message)) {
      return String(body.error.message);
    }
  }

  if (body?.error?.message && isHumanMessage(body.error.message)) {
    let msg = String(body.error.message);
    if (Array.isArray(body.error.details) && body.error.details.length) {
      const details = body.error.details
        .map((d: unknown) => String(d))
        .filter((d: string) => isHumanMessage(d));
      if (details.length) {
        msg += ': ' + details.join(', ');
      }
    }
    return msg;
  }

  if (body?.message && isHumanMessage(body.message)) {
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

/** True when text is safe to show end users (not HTTP junk / bare codes / UUIDs). */
function isHumanMessage(msg: unknown): boolean {
  if (msg == null) {
    return false;
  }
  const s = String(msg).trim();
  if (!s) {
    return false;
  }
  // HTTP junk
  if (/^\d{3}\s*:\s*(OK|Error|Unknown)?$/i.test(s)) {
    return false;
  }
  if (/^Http failure response/i.test(s)) {
    return false;
  }
  if (/^\d{3}\s+OK$/i.test(s)) {
    return false;
  }
  // Bare machine codes (e.g. BENEFICIARY_EXISTS, ACCOUNT_NOT_FOUND)
  if (/^[A-Z][A-Z0-9_]{2,}$/.test(s)) {
    return false;
  }
  // UUID-only
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(s)) {
    return false;
  }
  return true;
}
