import { NotificationItem } from '../models/domain.model';

/**
 * Resolve in-app navigation path for a notification.
 * Prefer server-provided actionPath; fall back to actionType/id and legacy body parsing.
 */
export function resolveNotificationPath(
  n: NotificationItem,
  mode: 'customer' | 'ops' = 'customer',
): string | null {
  const path = (n.actionPath || '').trim();
  if (path.startsWith('/')) {
    return path;
  }

  const type = (n.actionType || '').trim().toUpperCase();
  const id = (n.actionId || '').trim();

  if (type === 'SUPPORT_TICKET' && id) {
    return mode === 'ops'
      ? `/admin/support-tickets?ticketId=${encodeURIComponent(id)}`
      : `/customer/support?ticketId=${encodeURIComponent(id)}`;
  }
  if (type === 'TRANSFER' && id) {
    return mode === 'ops'
      ? `/admin/transfers?q=${encodeURIComponent(id)}`
      : `/customer/payments/transfer?txnId=${encodeURIComponent(id)}`;
  }
  if (type === 'KYC' && id) {
    return mode === 'ops' ? `/admin/customers?q=${encodeURIComponent(id)}` : null;
  }

  const legacyTicket = parseLegacyTicketId(n.body || '');
  if (legacyTicket) {
    return mode === 'ops'
      ? `/admin/support-tickets?ticketId=${encodeURIComponent(legacyTicket)}`
      : `/customer/support?ticketId=${encodeURIComponent(legacyTicket)}`;
  }

  const legacyTxn = parseLegacyTransferId(n.body || '');
  if (legacyTxn) {
    return mode === 'ops'
      ? `/admin/transfers?q=${encodeURIComponent(legacyTxn)}`
      : `/customer/payments/transfer?txnId=${encodeURIComponent(legacyTxn)}`;
  }

  return null;
}

// Prefer regex literals — no string-escape doubling issues under Windows tooling.
const TICKET_ID_RE =
  /(?:^|\s)ticketId\s*=\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i;
const TRANSFER_ID_RE =
  /(?:Your\s+)?transfer\s+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i;
const UUID_LITERAL_RE =
  /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
const KV_UUID_RE =
  /\b(ticketId|userId|customerId|eventId|txn|transactionId)\s*=\s*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;

export function parseLegacyTicketId(body: string): string | null {
  if (!body) return null;
  const m = body.match(TICKET_ID_RE);
  return m?.[1] ?? null;
}

export function parseLegacyTransferId(body: string): string | null {
  if (!body) return null;
  const m = body.match(TRANSFER_ID_RE);
  return m?.[1] ?? null;
}

/**
 * Turn raw/legacy notification bodies into human-readable Vietnamese for end users.
 * Strips ticketId=/userId=/UUID dumps. Safe for already-human bodies.
 */
export function humanizeNotificationBody(
  body: string | null | undefined,
  template?: string | null,
): string {
  const raw = (body || '').trim();
  if (!raw) {
    return '';
  }

  const looksTechnical =
    /ticketId\s*=/i.test(raw) ||
    /userId\s*=/i.test(raw) ||
    /customerId\s*=/i.test(raw) ||
    /Your transfer\s+/i.test(raw) ||
    /New staff reply on/i.test(raw) ||
    /Staff requested more information/i.test(raw) ||
    /Support ticket (opened|resolved|rejected)/i.test(raw) ||
    /Customer replied on/i.test(raw) ||
    /^Ticket resolved\b/i.test(raw) ||
    /^Ticket rejected\b/i.test(raw) ||
    /KYC status changed/i.test(raw) ||
    /\bfrom\s*=/i.test(raw) ||
    /\bto\s*=PENDING/i.test(raw);

  if (!looksTechnical) {
    return collapse(raw);
  }

  // Exact format from old BE:
  // "New staff reply on ticket ticketId=... subject=Hello message=ban can lam gi?"
  const staffReplyKv = raw.match(
    /New staff reply on ticket\s+ticketId\s*=\s*\S+\s+subject\s*=\s*(.*?)\s+message\s*=\s*(.*)$/i,
  );
  if (staffReplyKv) {
    const subject = cleanField(staffReplyKv[1]) || 'hỗ trợ';
    const message = cleanField(staffReplyKv[2]);
    return message
      ? `Nhân viên đã phản hồi ticket "${subject}". Nội dung: ${message}`
      : `Nhân viên đã phản hồi ticket "${subject}".`;
  }

  const staffReplyQuoted = raw.match(/New staff reply on\s+"([^"]+)"\s*:\s*(.*)$/i);
  if (staffReplyQuoted) {
    const subject = cleanField(staffReplyQuoted[1]) || 'hỗ trợ';
    const message = cleanField(staffReplyQuoted[2]);
    return message
      ? `Nhân viên đã phản hồi ticket "${subject}". Nội dung: ${message}`
      : `Nhân viên đã phản hồi ticket "${subject}".`;
  }

  const needInfoKv = raw.match(
    /Staff requested more information(?:\s+on ticket)?\s+ticketId\s*=\s*\S+\s+subject\s*=\s*(.*?)\s+message\s*=\s*(.*)$/i,
  );
  if (needInfoKv) {
    const subject = cleanField(needInfoKv[1]) || 'hỗ trợ';
    const message = cleanField(needInfoKv[2]);
    return message
      ? `Nhân viên yêu cầu bổ sung thông tin cho ticket "${subject}". Nội dung: ${message}`
      : `Nhân viên yêu cầu bổ sung thông tin cho ticket "${subject}".`;
  }

  const needInfoQuoted = raw.match(
    /Staff requested more information on\s+"([^"]+)"\s*:\s*(.*)$/i,
  );
  if (needInfoQuoted) {
    const subject = cleanField(needInfoQuoted[1]) || 'hỗ trợ';
    const message = cleanField(needInfoQuoted[2]);
    return message
      ? `Nhân viên yêu cầu bổ sung thông tin cho ticket "${subject}". Nội dung: ${message}`
      : `Nhân viên yêu cầu bổ sung thông tin cho ticket "${subject}".`;
  }

  if (/Ticket resolved/i.test(raw) || template === 'SUPPORT_TICKET_RESOLVED') {
    const mResolved =
      raw.match(
        /Ticket resolved(?:\s+ticketId\s*=\s*\S+)?\s*(?:subject\s*=\s*(.*?))?(?:\s+note\s*=\s*(.*))?$/i,
      ) || raw.match(/Ticket resolved:\s*(.*)$/i);
    const subject = cleanField(mResolved?.[1]) || extractKv(raw, 'subject') || 'hỗ trợ';
    const note = cleanField(mResolved?.[2]) || extractKv(raw, 'note');
    return note
      ? `Ticket "${subject}" đã được giải quyết. Ghi chú: ${note}`
      : `Ticket "${subject}" đã được giải quyết.`;
  }

  if (/Ticket rejected/i.test(raw) || template === 'SUPPORT_TICKET_REJECTED') {
    const mRejected =
      raw.match(
        /Ticket rejected(?:\s+ticketId\s*=\s*\S+)?\s*(?:subject\s*=\s*(.*?))?(?:\s+reason\s*=\s*(.*))?$/i,
      ) || raw.match(/Ticket rejected:\s*(.*?)\s*[—-]\s*(.*)$/i);
    const subject = cleanField(mRejected?.[1]) || extractKv(raw, 'subject') || 'hỗ trợ';
    const reason = cleanField(mRejected?.[2]) || extractKv(raw, 'reason');
    return reason
      ? `Ticket "${subject}" đã bị từ chối. Lý do: ${reason}`
      : `Ticket "${subject}" đã bị từ chối.`;
  }

  if (/Support ticket opened/i.test(raw) || template === 'OPS_SUPPORT_TICKET_OPENED') {
    const subject = extractKv(raw, 'subject') || extractSubject(raw) || 'hỗ trợ';
    const cat = extractKv(raw, 'category');
    const pri = extractKv(raw, 'priority');
    const meta = [cat, pri].filter(Boolean).join(' · ');
    return meta ? `Ticket mới: "${subject}" (${meta}).` : `Ticket mới: "${subject}".`;
  }
  if (/Support ticket resolved/i.test(raw) || template === 'OPS_SUPPORT_TICKET_RESOLVED') {
    const subject = extractKv(raw, 'subject') || extractSubject(raw) || 'hỗ trợ';
    return `Ticket "${subject}" đã được giải quyết.`;
  }
  if (/Support ticket rejected/i.test(raw) || template === 'OPS_SUPPORT_TICKET_REJECTED') {
    const subject = extractKv(raw, 'subject') || extractSubject(raw) || 'hỗ trợ';
    const reason = extractKv(raw, 'reason');
    return reason
      ? `Ticket "${subject}" bị từ chối. Lý do: ${reason}`
      : `Ticket "${subject}" bị từ chối.`;
  }
  if (/Customer replied on/i.test(raw) || template === 'OPS_SUPPORT_TICKET_CUSTOMER_REPLY') {
    const mReply = raw.match(/Customer replied on\s+"([^"]+)"\s*:\s*(.*)$/i);
    const subject = cleanField(mReply?.[1]) || extractKv(raw, 'subject') || 'hỗ trợ';
    const message = cleanField(mReply?.[2]);
    return message
      ? `Khách phản hồi ticket "${subject}". Nội dung: ${message}`
      : `Khách phản hồi ticket "${subject}".`;
  }

  // Legacy KYC dumps: "KYC status changed customerId=... from=n/a to=PENDING name=DogfoodUser"
  if (/KYC status changed/i.test(raw) || template === 'OPS_KYC_UPDATED') {
    const name = extractKv(raw, 'name') || 'khách hàng';
    const from = extractKv(raw, 'from') || '—';
    const to = extractKv(raw, 'to') || '—';
    return `KYC của ${name} đổi từ ${from} → ${to}.`;
  }

  const transferOk = raw.match(
    /Your transfer\s+[0-9a-f-]{36}\s+of\s+([0-9.]+)\s+([A-Z]{3})\s+completed successfully\.?\s*(.*)$/i,
  );
  if (transferOk || template === 'TRANSFER_COMPLETED') {
    const amount = transferOk?.[1] || '';
    const currency = transferOk?.[2] || 'VND';
    const desc = cleanField(transferOk?.[3] || '');
    const head = amount
      ? `Chuyển khoản ${formatAmount(amount)} ${currency} thành công.`
      : 'Chuyển khoản thành công.';
    return desc ? `${head} ${desc}` : head;
  }

  const transferFail = raw.match(
    /Your transfer\s+[0-9a-f-]{36}\s+ended as\s+(\w+)\.?\s*Reason:\s*(.*?)\.?\s*Amount:\s*([0-9.]+)\s*([A-Z]{3})?/i,
  );
  if (transferFail || template === 'TRANSFER_FAILED') {
    const status = transferFail?.[1] || 'FAILED';
    const reason = cleanField(transferFail?.[2] || 'n/a');
    const amount = transferFail?.[3] || '';
    const currency = transferFail?.[4] || 'VND';
    const amt = amount ? ` (${formatAmount(amount)} ${currency})` : '';
    return `Chuyển khoản không thành công${amt}. Trạng thái: ${status}. Lý do: ${reason}`;
  }

  let cleaned = raw
    .replace(KV_UUID_RE, '')
    .replace(UUID_LITERAL_RE, '')
    .replace(
      /\b(subject|message|note|reason|category|priority|from|to|name|by|amount|status)\s*=\s*/gi,
      '',
    )
    .replace(/\s{2,}/g, ' ')
    .replace(/\s+([.,;:!?])/g, '$1')
    .trim();

  return cleaned || 'Bạn có thông báo mới.';
}

function extractSubject(body: string): string | null {
  const m =
    body.match(
      /subject\s*=\s*([^\n]+?)(?:\s+(?:message|note|reason|ticketId|userId|category|priority)\s*=|$)/i,
    ) || body.match(/subject\s*=\s*(.+)$/i);
  return m ? cleanField(m[1]) : null;
}

function extractKv(body: string, key: string): string | null {
  const re = new RegExp(key + '\s*=\s*([^\n]+)', 'i');
  const m = body.match(re);
  if (!m) return null;
  const val = m[1].replace(/\s+\w+\s*=.*$/, '').trim();
  return cleanField(val);
}

/**
 * Fallback when i18n has no NOTIF_TEMPLATE.* key — never show raw OPS_SUPPORT_TICKET_OPENED codes.
 */
export function humanizeTemplateCode(template: string | null | undefined): string {
  const t = (template || '').trim();
  if (!t) return 'Thông báo';

  const known: Record<string, string> = {
    TRANSFER_COMPLETED: 'Chuyển khoản thành công',
    TRANSFER_FAILED: 'Chuyển khoản thất bại',
    OPS_TRANSFER_FAILED: 'Chuyển khoản thất bại',
    OPS_OUTBOX_DEAD: 'Outbox bị kẹt (DEAD)',
    OPS_ACCOUNT_FROZEN: 'Tài khoản bị đóng băng',
    OPS_ACCOUNT_UNFROZEN: 'Tài khoản được mở khóa',
    OPS_KYC_UPDATED: 'Cập nhật KYC',
    SUPPORT_TICKET_STAFF_REPLY: 'Nhân viên phản hồi ticket',
    SUPPORT_TICKET_NEED_INFO: 'Yêu cầu bổ sung thông tin',
    SUPPORT_TICKET_RESOLVED: 'Ticket đã xử lý',
    SUPPORT_TICKET_REJECTED: 'Ticket bị từ chối',
    SUPPORT_TICKET_MENTION: 'Bạn được nhắc đến trên ticket',
    OPS_SUPPORT_TICKET_OPENED: 'Ticket hỗ trợ mới',
    OPS_SUPPORT_TICKET_RESOLVED: 'Ticket đã giải quyết',
    OPS_SUPPORT_TICKET_REJECTED: 'Ticket bị từ chối',
    OPS_SUPPORT_TICKET_CUSTOMER_REPLY: 'Khách phản hồi ticket',
  };
  if (known[t]) {
    return known[t];
  }

  // Generic: OPS_SUPPORT_TICKET_OPENED → "Support ticket opened"
  return t
    .replace(/^OPS_/, '')
    .split('_')
    .filter(Boolean)
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

function cleanField(v: string | null | undefined): string {
  if (!v) return '';
  return collapse(
    v
      .replace(UUID_LITERAL_RE, '')
      .replace(/\b(ticketId|userId|message|subject|note|reason)\s*=/gi, '')
      .replace(/^["'\s]+|["'\s]+$/g, ''),
  );
}

function collapse(s: string): string {
  return s.replace(/\s+/g, ' ').trim();
}

function formatAmount(amount: string): string {
  const n = Number(amount);
  if (!Number.isFinite(n)) return amount;
  try {
    return new Intl.NumberFormat('vi-VN').format(n);
  } catch {
    return amount;
  }
}
