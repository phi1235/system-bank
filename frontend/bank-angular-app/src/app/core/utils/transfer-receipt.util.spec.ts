import {
  buildTransferReceiptText,
  canRetryTransfer,
  transferRetryQueryParams,
} from './transfer-receipt.util';
import { Transfer } from '../models/domain.model';

class FakeI18n {
  instant(key: string, params?: Record<string, unknown>): string {
    if (key.startsWith('TRANSFER_STATUS.')) {
      return key.replace('TRANSFER_STATUS.', '');
    }
    if (params) {
      return `${key}:${JSON.stringify(params)}`;
    }
    return key;
  }
}

describe('transfer-receipt.util', () => {
  const sample: Transfer = {
    transactionId: 'abc-123-def',
    status: 'FAILED',
    fromAccountId: 'from-1',
    toAccountId: null,
    toAccountNumber: '1234567890',
    amount: 100000,
    feeAmount: 1000,
    currency: 'VND',
    description: 'test',
    failureReason: 'INSUFFICIENT_BALANCE: not enough',
    createdAt: '2026-07-22T08:00:00.000Z',
  };

  it('canRetry only for FAILED/COMPENSATED', () => {
    expect(canRetryTransfer('FAILED')).toBe(true);
    expect(canRetryTransfer('COMPENSATED')).toBe(true);
    expect(canRetryTransfer('COMPLETED')).toBe(false);
    expect(canRetryTransfer('PENDING')).toBe(false);
  });

  it('builds retry query params for form prefill', () => {
    expect(transferRetryQueryParams(sample)).toEqual({
      retry: '1',
      from: 'from-1',
      to: '1234567890',
      amount: '100000',
      desc: 'test',
    });
  });

  it('builds multi-line receipt text without raw HTTP junk', () => {
    const text = buildTransferReceiptText(sample, new FakeI18n() as never, {
      statusLabel: 'Failed',
      reasonLabel: 'Insufficient balance',
    });
    expect(text).toContain('TRANSFER_DETAIL.RECEIPT_TITLE');
    expect(text).toContain('abc-123-def');
    expect(text).toContain('1234567890');
    expect(text).not.toMatch(/500:\s*OK/);
  });
});
