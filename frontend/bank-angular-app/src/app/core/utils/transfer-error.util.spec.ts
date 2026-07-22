import { parseTransferError, transferErrorI18nKey } from './transfer-error.util';

describe('transfer-error.util', () => {
  it('parses CODE: message', () => {
    const p = parseTransferError('INSUFFICIENT_BALANCE: Account balance is insufficient');
    expect(p.code).toBe('INSUFFICIENT_BALANCE');
    expect(p.detail).toContain('insufficient');
  });

  it('prefers explicit api code', () => {
    const p = parseTransferError('Account balance is insufficient', 'INSUFFICIENT_BALANCE');
    expect(p.code).toBe('INSUFFICIENT_BALANCE');
  });

  it('detects COMPENSATION_PARTIAL prefix', () => {
    const p = parseTransferError('COMPENSATION_PARTIAL: reverse dest failed: x | y');
    expect(p.code).toBe('COMPENSATION_PARTIAL');
  });

  it('returns null code for free text', () => {
    const p = parseTransferError('something went wrong');
    expect(p.code).toBeNull();
    expect(p.detail).toBe('something went wrong');
  });

  it('builds i18n key', () => {
    expect(transferErrorI18nKey('daily_limit_exceeded')).toBe('TRANSFER_ERROR.DAILY_LIMIT_EXCEEDED');
    expect(transferErrorI18nKey(null)).toBeNull();
  });
});
