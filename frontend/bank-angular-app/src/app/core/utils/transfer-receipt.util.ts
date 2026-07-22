import { TranslateService } from '@ngx-translate/core';
import { Transfer } from '../models/domain.model';

/** Build a plain-text receipt for clipboard / share (user-facing labels only). */
export function buildTransferReceiptText(
  t: Transfer,
  i18n: TranslateService,
  opts?: { statusLabel?: string; reasonLabel?: string },
): string {
  const money = (n: number | undefined | null) =>
    Number(n || 0).toLocaleString('vi-VN') + ' ' + (t.currency || 'VND');

  const statusLabel =
    opts?.statusLabel ||
    (() => {
      const key = `TRANSFER_STATUS.${t.status}`;
      const loc = i18n.instant(key);
      return loc && loc !== key ? loc : t.status;
    })();

  const lines = [
    i18n.instant('TRANSFER_DETAIL.RECEIPT_TITLE'),
    i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_STATUS', { status: statusLabel }),
    i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_AMOUNT', { amount: money(t.amount) }),
    i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_FEE', { fee: money(t.feeAmount) }),
  ];

  if (t.fromAccountId) {
    lines.push(
      i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_FROM', { from: t.fromAccountId }),
    );
  }
  lines.push(
    i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_TO', {
      to: t.toAccountNumber || '—',
    }),
  );
  if (t.createdAt) {
    const time = new Date(t.createdAt).toLocaleString('vi-VN');
    lines.push(i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_TIME', { time }));
  }
  lines.push(i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_ID', { id: t.transactionId }));
  if (t.description) {
    lines.push(
      i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_DESC', { desc: t.description }),
    );
  }
  if (t.failureReason) {
    const reason = opts?.reasonLabel || t.failureReason;
    lines.push(i18n.instant('TRANSFER_DETAIL.RECEIPT_LINE_REASON', { reason }));
  }
  return lines.join('\n');
}

export function canRetryTransfer(status: string | null | undefined): boolean {
  return status === 'FAILED' || status === 'COMPENSATED';
}

/** Query params used to prefill transfer form for a new attempt. */
export function transferRetryQueryParams(t: Transfer): Record<string, string> {
  const q: Record<string, string> = { retry: '1' };
  if (t.fromAccountId) {
    q['from'] = t.fromAccountId;
  }
  if (t.toAccountNumber) {
    q['to'] = t.toAccountNumber;
  }
  if (t.amount != null) {
    q['amount'] = String(t.amount);
  }
  if (t.description) {
    q['desc'] = t.description;
  }
  return q;
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(ta);
      return ok;
    } catch {
      return false;
    }
  }
}
