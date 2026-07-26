export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  accountType: string;
  currency: string;
  balance: number;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TopUpResponse {
  accountId: string;
  accountNumber: string;
  ledgerEntryId: string;
  referenceId: string;
  amount: number;
  balanceAfter: number;
  channel: string;
}

export interface CustomerProfile {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  nationalIdMasked: string | null;
  kycStatus: string;
  address: string | null;
}

export interface Transfer {
  transactionId: string;
  status: string;
  fromAccountId: string;
  toAccountId: string | null;
  toAccountNumber: string | null;
  amount: number;
  /** Fee charged on source in addition to amount (skeleton; may be 0). */
  feeAmount?: number;
  currency: string;
  description: string | null;
  failureReason: string | null;
  createdAt: string;
}

export interface SagaStep {
  id: string;
  step: string;
  status: string;
  detail: string | null;
  createdAt: string;
}

export interface TransferDetail {
  transfer: Transfer;
  steps: SagaStep[];
}

export interface BankItem {
  bankCode: string;
  shortName: string;
  fullName: string;
  bin: string;
  logoUrl: string;
  napasSupported: boolean;
  isInternal: boolean;
}

export interface AccountInquiryRequest {
  bankCode?: string;
  accountNumber: string;
}

export interface AccountInquiryResponse {
  bankCode: string;
  accountNumber: string;
  accountName: string;
  isInternal: boolean;
  accountId?: string | null;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountNumber: string;
  amount: number;
  description?: string;
  currency?: string;
  transferType?: 'INTERNAL' | 'INTERBANK' | string;
  targetBankCode?: string;
  targetAccountName?: string;
}

/** Pre-transfer fee + daily limit remaining preview (includes fee formula breakdown). */
export interface TransferQuote {
  amount: number;
  feeAmount: number;
  totalDebit: number;
  maxPerTransaction: number;
  dailyLimit: number;
  spentToday: number;
  remainingToday: number;
  currency: string;
  dailyLimitZone: string;
  feeEnabled: boolean;
  /** Flat fee component from config (VND). */
  feeFlat?: number;
  /** Percent of principal (e.g. 0.1 = 0.1%). */
  feePercent?: number;
  /** Computed percent portion for this amount. */
  feePercentAmount?: number;
  feeMin?: number;
  feeMax?: number;
  /** flat + percentAmount before min/max clamp. */
  feeRawBeforeClamp?: number;
  feeCappedByMin?: boolean;
  feeCappedByMax?: boolean;
}

/** Account ledger line (DEBIT/CREDIT), not transfer-order history. */
export interface LedgerEntry {
  id: string;
  accountId: string;
  entryType: 'DEBIT' | 'CREDIT' | string;
  amount: number;
  signedAmount: number;
  referenceId: string | null;
  description: string | null;
  createdAt: string;
}

export interface Beneficiary {
  id: string;
  nickname: string;
  accountNumber: string;
  accountId: string | null;
  currency: string;
  active: boolean;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  actorUserId: string | null;
  action: string;
  resourceType: string;
  resourceId: string;
  ip: string | null;
  metadata: string | null;
  createdAt: string;
}

/** Outbox event for ops (Kafka publish lifecycle). */
export interface OutboxEvent {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  status: 'PENDING' | 'PUBLISHED' | 'DEAD' | string;
  attemptCount: number;
  nextAttemptAt: string | null;
  createdAt: string;
  publishedAt: string | null;
  lastError: string | null;
  /** Present on detail/replay responses; usually null on list rows. */
  payload?: string | null;
}

export interface OutboxCounts {
  pending: number;
  published: number;
  dead: number;
}

/** Customer IB notification inbox item (delivery log scoped to user). */
export interface NotificationItem {
  id: string;
  channel: string;
  template: string;
  status: string;
  body: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
  /** Optional deep-link entity type (SUPPORT_TICKET, TRANSFER, ...). */
  actionType?: string | null;
  /** Optional deep-link entity id. */
  actionId?: string | null;
  /** Preferred in-app path, e.g. /customer/support?ticketId=... */
  actionPath?: string | null;
}

/** Customer support ticket (create + staff approve/resolve). */
export interface SupportTicketMessage {
  id: string;
  ticketId: string;
  authorUserId: string;
  authorRole: string;
  body: string;
  createdAt: string;
}

export interface SupportTicket {
  id: string;
  userId: string;
  category: string;
  subject: string;
  body: string;
  priority: string;
  status: string;
  requesterEmail: string | null;
  resolutionNote: string | null;
  rejectReason: string | null;
  assignedTo: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
  rejectedAt: string | null;
  rejectedBy: string | null;
  messages?: SupportTicketMessage[] | null;
}

/** One banking-day bucket of the admin transaction report. */
export interface DailyVolumePoint {
  day: string;
  totalCount: number;
  completedCount: number;
  failedCount: number;
  completedAmount: number;
  feeAmount: number;
}

export interface StatusBreakdownRow {
  status: string;
  count: number;
  totalAmount: number;
}

export interface TopAccountRow {
  fromAccountId: string;
  transferCount: number;
  totalAmount: number;
}

export interface TransactionReport {
  from: string;
  to: string;
  zone: string;
  totalCount: number;
  completedCount: number;
  failedCount: number;
  successRate: number;
  completedAmount: number;
  feeAmount: number;
  avgCompletedAmount: number;
  daily: DailyVolumePoint[];
  byStatus: StatusBreakdownRow[];
  topSourceAccounts: TopAccountRow[];
}

/** End-of-day reconciliation run (transfer_orders vs account-service ledger). */
export interface ReconRun {
  id: string;
  businessDate: string;
  zone: string;
  triggerType: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  ordersChecked: number;
  ledgerEntriesSeen: number;
  discrepancyCount: number;
  errorDetail: string | null;
}

export interface ReconItem {
  id: string;
  transferId: string | null;
  kind: string;
  entryRef: string | null;
  expectedAmount: number | null;
  actualAmount: number | null;
  detail: string | null;
}

export interface ReconRunDetail {
  run: ReconRun;
  items: ReconItem[];
}

/** Term-deposit (so tiet kiem) product offered to customers. */
export interface DepositProduct {
  code: string;
  tenorMonths: number;
  rateBps: number;
  earlyRateBps: number;
  minAmount: number;
  active: boolean;
}

export interface DepositQuote {
  productCode: string;
  tenorMonths: number;
  rateBps: number;
  amount: number;
  openDate: string;
  maturityDate: string;
  days: number;
  expectedInterest: number;
  totalAtMaturity: number;
}

export interface TermDeposit {
  id: string;
  sourceAccountId: string;
  productCode: string;
  tenorMonths: number;
  amount: number;
  rateBps: number;
  earlyRateBps: number;
  openedAt: string;
  maturityDate: string;
  status: string;
  interest: number;
  closedAt: string | null;
}

/** Admin funding summary over term deposits. */
export interface DepositTotals {
  openCount: number;
  openPrincipal: number;
  openAccrued: number;
  dueIn7Days: number;
  maturedCount: number;
  closedEarlyCount: number;
}

export interface DepositTenorSummary {
  code: string;
  tenorMonths: number;
  rateBps: number;
  openCount: number;
  openPrincipal: number;
  openAccrued: number;
}

export interface DepositAdminSummary {
  totals: DepositTotals;
  byProduct: DepositTenorSummary[];
}

export interface DepositBatchResult {
  accruedUpdated: number;
  matured: number;
  failed: number;
}

/** Admin drill-down row: one term-deposit contract with its owner. */
export interface AdminTermDeposit {
  id: string;
  userId: string;
  ownerName: string | null;
  sourceAccountId: string;
  sourceAccountNumber: string | null;
  productCode: string;
  tenorMonths: number;
  amount: number;
  rateBps: number;
  accruedInterest: number;
  openedAt: string;
  maturityDate: string;
  status: string;
  closedAt: string | null;
}
