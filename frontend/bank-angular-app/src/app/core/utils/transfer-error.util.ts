/**
 * Parse backend transfer failure payloads into a stable business code + detail.
 *
 * Stored failureReason formats (saga / policies):
 * - "INSUFFICIENT_BALANCE: Account balance is insufficient"
 * - "DEBIT_ERROR: ..."
 * - "COMPENSATION_PARTIAL: reverse dest failed: ... | ..."
 * - plain English message without a code prefix
 *
 * API error body uses { error: { code, message } } — pass code separately when available.
 */

export interface ParsedTransferError {
  /** Normalized business code, e.g. INSUFFICIENT_BALANCE */
  code: string | null;
  /** Human detail after the code prefix, or the full raw string */
  detail: string;
  raw: string;
}

const KNOWN_CODES = new Set([
  'INSUFFICIENT_BALANCE',
  'TRANSFER_LIMIT_EXCEEDED',
  'DAILY_LIMIT_EXCEEDED',
  'ACCOUNT_FROZEN',
  'ACCOUNT_NOT_FOUND',
  'INVALID_AMOUNT',
  'SAME_ACCOUNT',
  'FORBIDDEN',
  'IDEMPOTENCY_REQUIRED',
  'IDEMPOTENCY_CONFLICT',
  'TRANSFER_NOT_FOUND',
  'INVALID_DATE_RANGE',
  'SAGA_INJECTED_FAIL',
  'FEE_GL_FAILED',
  'ACCOUNT_SERVICE_ERROR',
  'DEBIT_ERROR',
  'COMPENSATION_PARTIAL',
  'VALIDATION_ERROR',
  'INTERNAL_ERROR',
  'TRANSFER_FAILED',
  'BENEFICIARY_INQUIRY_REQUIRED',
  'BENEFICIARY_INQUIRY_INVALID',
  'BENEFICIARY_INQUIRY_EXPIRED',
  'BENEFICIARY_INQUIRY_CONSUMED',
  'BENEFICIARY_INQUIRY_MISMATCH',
  'BENEFICIARY_INQUIRY_UNAVAILABLE',
  'BANK_NOT_SUPPORTED',
  'TARGET_BANK_REQUIRED',
]);

/** Prefixes we treat as codes even when not in the known list (UPPER_SNAKE). */
const CODE_PREFIX = /^([A-Z][A-Z0-9_]{2,63})\s*:\s*(.*)$/s;

export function parseTransferError(
  reason: string | null | undefined,
  apiCode?: string | null,
): ParsedTransferError {
  const raw = (reason ?? '').trim();
  const fromApi = normalizeCode(apiCode);
  if (fromApi) {
    return { code: fromApi, detail: raw || fromApi, raw: raw || fromApi };
  }
  if (!raw) {
    return { code: null, detail: '', raw: '' };
  }
  const m = CODE_PREFIX.exec(raw);
  if (m) {
    const code = normalizeCode(m[1]);
    const detail = (m[2] ?? '').trim() || raw;
    return { code, detail, raw };
  }
  // Heuristic: whole token is a known code
  const asCode = normalizeCode(raw);
  if (asCode && KNOWN_CODES.has(asCode)) {
    return { code: asCode, detail: raw, raw };
  }
  return { code: null, detail: raw, raw };
}

export function transferErrorI18nKey(code: string | null | undefined): string | null {
  const c = normalizeCode(code);
  if (!c) return null;
  return `TRANSFER_ERROR.${c}`;
}

function normalizeCode(code: string | null | undefined): string | null {
  if (!code) return null;
  const c = code.trim().toUpperCase();
  if (!/^[A-Z][A-Z0-9_]{2,63}$/.test(c)) return null;
  return c;
}
