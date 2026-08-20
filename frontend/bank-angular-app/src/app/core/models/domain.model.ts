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

export interface SepayTopUpRequest {
  accountNumber: string;
  amount: number;
  note?: string;
}

export interface SepayTopUpOrder {
  id: string;
  orderCode: string;
  accountNumber: string;
  amount: number;
  status: 'PENDING' | 'SUCCESS' | 'EXPIRED' | 'CANCELLED' | 'MANUAL_REVIEW' | string;
  vietQrUrl: string;
  bankName: string;
  bankAccount: string;
  accountName: string;
  transferContent: string;
  createdAt: string;
  expiresAt: string;
  completedAt?: string;
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
  transferType?: 'INTERNAL' | 'INTERBANK' | string;
  targetBankCode?: string | null;
  targetAccountName?: string | null;
  riskDecision?: string | null;
  riskScore?: number | null;
  riskReason?: string | null;
}

export interface RiskRule {
  id: string;
  code: string;
  ruleType: 'AMOUNT' | 'VELOCITY_COUNT' | 'VELOCITY_TOTAL' | string;
  action: 'ALLOW' | 'ALERT' | 'REVIEW' | 'BLOCK' | string;
  enabled: boolean;
  priority: number;
  thresholdAmount: number | null;
  windowSeconds: number | null;
  maxCount: number | null;
  maxTotalAmount: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RiskBlacklistEntry {
  id: string;
  subjectType: 'USER' | 'ACCOUNT' | 'BANK' | string;
  subjectValue: string;
  reason: string;
  active: boolean;
  expiresAt: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface KycDocument {
  id: string;
  documentType: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  scanStatus: string;
  uploadedAt: string;
}

export interface KycHistory {
  id: string;
  actorId: string;
  action: string;
  fromStatus: string | null;
  toStatus: string;
  note: string | null;
  createdAt: string;
}

export interface KycCase {
  id: string;
  customerId: string;
  status: string;
  makerId: string | null;
  makerRecommendation: string | null;
  makerNote: string | null;
  makerAt: string | null;
  checkerId: string | null;
  decision: string | null;
  decisionReason: string | null;
  submittedAt: string | null;
  decidedAt: string | null;
  documents: KycDocument[];
  history: KycHistory[];
  createdAt: string;
  updatedAt: string;
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
  bankBin?: string;
  bankCode?: string;
  accountNumber: string;
}

export interface AccountInquiryResponse {
  inquiryId?: string;
  bank?: {
    bin: string;
    code: string;
    shortName: string;
  };
  accountNumberMasked?: string;
  bankCode?: string;
  accountNumber?: string;
  accountName: string;
  accountType?: 'INTERNAL' | 'INTERBANK' | string;
  status?: string;
  provider?: string;
  verifiedAt?: string;
  expiresAt?: string;
  isInternal?: boolean;
  accountId?: string | null;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountNumber?: string;
  amount: number;
  description?: string;
  currency?: string;
  transferType?: 'INTERNAL' | 'INTERBANK' | string;
  targetBankCode?: string;
  targetAccountName?: string;
  inquiryId?: string;
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

/** Virtual debit card; PAN always masked except explicit reveal. */
export interface Card {
  id: string;
  accountId: string;
  accountNumber: string | null;
  /** Null while the request awaits approval. */
  maskedPan: string | null;
  brand: string;
  status: string;
  dailyLimit: number;
  expiresOn: string | null;
  /** Set only when status is REJECTED. */
  rejectReason: string | null;
  createdAt: string;
}

/** Staff approval-queue row. */
export interface AdminCard {
  id: string;
  userId: string;
  ownerName: string | null;
  accountId: string;
  accountNumber: string | null;
  status: string;
  dailyLimit: number;
  rejectReason: string | null;
  requestedAt: string;
  email?: string | null;
  phone?: string | null;
  kycStatus?: string | null;
}

export interface BatchApproveResult {
  approvedCount: number;
  failedCount: number;
  errors: string[];
}

export interface CardReveal {
  id: string;
  pan: string;
  expiresOn: string;
}

// ── Bill Payments ──

export interface BillCategoryItem {
  id: string;
  name: string;
  iconUrl: string;
  icon?: string;
  sampleCode?: string;
  themeClass?: string;
  displayOrder: number;
}

export interface BillProviderItem {
  id: string;
  categoryId: string;
  name: string;
  code: string;
}

export interface BillInquiryResult {
  customerName: string;
  amount: number;
  period: string;
  providerId: string;
  customerCode: string;
}

export interface BillPayResult {
  paymentId: string;
  status: string;
  transactionRef: string;
  amount: number;
  fee: number;
  createdAt: string;
}

export interface BillPaymentHistory {
  id: string;
  categoryId: string;
  providerId: string;
  customerCode: string;
  customerName: string;
  amount: number;
  fee: number;
  status: string;
  transactionRef: string;
  createdAt: string;
}

export interface ForensicInvestigation {
  transactionId: string;
  status: string;
  transferType: string;
  fromAccountId: string;
  toAccountId: string | null;
  toAccountNumber: string;
  targetBankCode: string | null;
  targetAccountName: string | null;
  amount: number;
  feeAmount: number;
  currency: string;
  riskDecision: string | null;
  riskScore: number | null;
  providerStatus: string | null;
  failureReason: string | null;
  needsAttention: boolean;
  primarySignal: string;
  createdAt: string;
  updatedAt: string;
}

export interface ForensicSagaEvidence {
  id: string;
  step: string;
  status: string;
  detail: string | null;
  occurredAt: string;
}

export interface ForensicOutboxEvidence {
  id: string;
  eventType: string;
  status: string;
  attemptCount: number;
  lastError: string | null;
  occurredAt: string;
  publishedAt: string | null;
}

export interface ForensicReconciliationEvidence {
  id: string;
  runId: string;
  kind: string;
  entryRef: string | null;
  expectedAmount: number | null;
  actualAmount: number | null;
  detail: string | null;
}

export interface ForensicAuditEvidence {
  id: string;
  actorUserId: string | null;
  action: string;
  resourceType: string | null;
  detail: string | null;
  occurredAt: string;
}

export interface ForensicTimelineEvidence {
  source: string;
  sourceId: string;
  event: string;
  status: string | null;
  detail: string | null;
  occurredAt: string;
}

export interface ForensicLedgerPostingEvidence {
  id: string;
  accountId: string | null;
  ledgerAccountCode: string;
  side: string;
  amount: number;
  currency: string;
  createdAt: string;
}

export interface ForensicLedgerJournalEvidence {
  id: string;
  businessCommandId: string;
  businessReference: string;
  journalType: string;
  status: string;
  currency: string;
  description: string | null;
  reversalOfJournalId: string | null;
  sequenceNo: number;
  createdAt: string;
  postedAt: string | null;
  postings: ForensicLedgerPostingEvidence[];
}

export interface ForensicLedgerHoldEvidence {
  id: string;
  accountId: string;
  amount: number;
  currency: string;
  status: string;
  expiresAt: string | null;
  capturedJournalId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ForensicFinancialEventEvidence {
  eventId: string;
  aggregateType: string;
  aggregateId: string;
  sequenceNo: number;
  eventType: string;
  schemaVersion: number;
  occurredAt: string;
  payload: Record<string, unknown>;
  payloadSha256: string;
}

export interface ForensicLedgerEvidence {
  available: boolean;
  completeness: string;
  journals: ForensicLedgerJournalEvidence[];
  holds: ForensicLedgerHoldEvidence[];
  events: ForensicFinancialEventEvidence[];
}

export interface ForensicFinancialViolation {
  ruleCode: string;
  severity: string;
  status: string;
  message: string;
  evidenceIds: string[];
}

export interface ForensicCausalNode {
  id: string;
  type: string;
  label: string;
  status: string | null;
  occurredAt: string | null;
  anomalous: boolean;
}

export interface ForensicCausalEdge {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  relation: string;
}

export interface ForensicCausalGraph {
  nodes: ForensicCausalNode[];
  edges: ForensicCausalEdge[];
  firstAnomalousNodeId: string | null;
  failureSignature: string | null;
  completeness: string;
}

export interface ForensicTemporalAccountState {
  accountId: string;
  currency: string;
  ledgerBalance: number;
  activeHoldAmount: number;
  availableBalance: number;
  completeness: string;
}

export interface ForensicTemporalState {
  transactionId: string;
  at: string;
  transactionState: string;
  accountStates: ForensicTemporalAccountState[];
  missingSources: string[];
  completeness: string;
}

export interface ForensicVerificationRuleResult {
  id: string;
  ruleCode: string;
  outcome: string;
  severity: string;
  message: string;
  evidence: Record<string, unknown>;
  evaluatedAt: string;
}

export interface ForensicVerificationRun {
  id: string;
  transactionId: string;
  ruleSetVersion: string;
  status: string;
  outcome: string | null;
  sourceWatermark: string | null;
  startedAt: string;
  completedAt: string | null;
  results: ForensicVerificationRuleResult[];
}

export interface ForensicEvidenceExport {
  id: string;
  caseId: string;
  status: string;
  sensitivity: string;
  packageSha256: string | null;
  errorDetail: string | null;
  createdAt: string;
  completedAt: string | null;
  expiresAt: string;
}

export interface ForensicTwinFork {
  id: string;
  transactionId: string;
  status: string;
  snapshotSha256: string;
  schemaVersion: number;
  createdAt: string;
  expiresAt: string;
}

export interface ForensicReplayRun {
  id: string;
  forkId: string;
  scenarioId: string;
  seed: number;
  targetCommitSha: string;
  status: string;
  resultSha256: string | null;
  errorDetail: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  expiresAt: string;
}

export interface ForensicReplayScenario {
  scenarioId: string;
  title: string;
  engineKey: string;
  sourceIncidentId: string;
  sourceEvidenceRef: string;
  definition: Record<string, unknown>;
  sanitized: boolean;
  status: string;
  createdBy: string;
  confirmedBy: string | null;
  confirmedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ForensicCopilotSession {
  id: string;
  transactionId: string | null;
  caseId: string | null;
  status: string;
  createdAt: string;
  expiresAt: string;
}

export interface ForensicCopilotCitation {
  sourceType: string;
  sourceId: string;
  label: string;
}

export interface ForensicCopilotAnswer {
  messageId: string;
  answer: string;
  status: string;
  toolCalls: string[];
  citations: ForensicCopilotCitation[];
  validation: Record<string, unknown>;
  createdAt: string;
}

export interface ForensicInvestigationDetail {
  transaction: ForensicInvestigation;
  evidenceCompleteness: string;
  missingSources: string[];
  sagaSteps: ForensicSagaEvidence[];
  outboxEvents: ForensicOutboxEvidence[];
  reconciliationItems: ForensicReconciliationEvidence[];
  auditEvents: ForensicAuditEvidence[];
  ledgerEvidence: ForensicLedgerEvidence;
  violations: ForensicFinancialViolation[];
  causalGraph: ForensicCausalGraph;
  timeline: ForensicTimelineEvidence[];
}

export interface ForensicBusinessNarrative {
  summary: string;
  impactAnalysis: string;
  rootCauseNarrative: string;
  suggestedRemediationNarrative: string;
  groundedEvidenceKeys: string[];
  generatedBy: string;
  generatedAt: string;
}

export interface ForensicCase {
  id: string;
  caseNumber: string;
  transactionId: string | null;
  accountId: string | null;
  sourceType: string;
  sourceReferenceId: string | null;
  status: string;
  investigationStage: string;
  priority: string;
  title: string;
  summary: string | null;
  evidenceCompleteness: string;
  assignedTo: string | null;
  createdBy: string;
  submittedBy: string | null;
  checkerId: string | null;
  resolutionCode: string | null;
  resolutionNote: string | null;
  remediationStatus: string;
  remediationActions: RemediationAction[];
  businessNarrative?: ForensicBusinessNarrative | null;
  systemic: boolean;
  investigationCycle: number;
  version: number;
  submittedAt: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RemediationAction {
  actionType: string;
  referenceId: string | null;
  description: string;
  completed: boolean;
  completedAt: string | null;
}

export interface ForensicFinding {
  id: string;
  findingKey: string;
  ruleCode: string;
  outcome: string;
  severity: string;
  disposition: string;
  title: string;
  detail: string | null;
  evidence: Record<string, unknown>;
  evidenceHash: string;
  occurrenceCount: number;
  detectedAt: string;
  lastSeenAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolutionReason: string | null;
  resolutionEvidence: Record<string, unknown>;
  resolvedBy: string | null;
  resolvedAt: string | null;
  version: number;
}

export interface ForensicCaseDetail {
  forensicCase: ForensicCase;
  findings: ForensicFinding[];
}

export interface ForensicCaseHistory {
  id: string;
  actorUserId: string;
  action: string;
  fromStatus: string | null;
  toStatus: string;
  decision: string | null;
  note: string | null;
  caseVersion: number;
  createdAt: string;
}

export interface SandboxTopupRequest {
  accountId: string;
  amount: number;
}

export interface SandboxTopupResponse {
  accountId: string;
  amount: number;
  balanceAfter: number;
  accumulatedToday: number;
  remainingQuotaToday: number;
}

export interface SandboxConfigResponse {
  enabled: boolean;
  maxDailyQuota: number;
}

export interface ForceActionRequest {
  reason: string;
}

export interface BusinessOrganization {
  id: string;
  code: string;
  name: string;
  taxCode: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'INACTIVE';
  createdAt: string;
  updatedAt?: string;
}

export interface BusinessMember {
  id: string;
  organizationId: string;
  userId: string;
  businessRole: 'BUSINESS_OWNER' | 'BUSINESS_FINANCE' | 'BUSINESS_OPERATOR' | 'BUSINESS_VIEWER';
  status: 'ACTIVE' | 'SUSPENDED';
  userEmail?: string;
  userFullName?: string;
  createdAt: string;
}

export interface BusinessMembership {
  organization: BusinessOrganization;
  businessRole: string;
  permissions: string[];
}

export interface VirtualAccount {
  id: string;
  organizationId: string;
  provider: string;
  bankBin: string;
  accountNumber: string;
  parentAccountId?: string;
  mode: 'SINGLE_USE' | 'FIXED_PAYER';
  customerReference?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'CLOSED' | 'EXPIRED';
  vietQrUrl: string;
  activatedAt?: string;
  expiresAt?: string;
  createdAt: string;
}

export interface CollectionOrder {
  id: string;
  organizationId: string;
  merchantOrderId: string;
  virtualAccountId: string;
  virtualAccountNumber: string;
  bankBin: string;
  vietQrUrl: string;
  expectedAmount: number;
  paidAmount: number;
  currency: string;
  status: 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERPAID' | 'EXPIRED' | 'CANCELLED' | 'REVIEW';
  customerReference?: string;
  splitRuleSnapshot?: string;
  expiresAt?: string;
  paidAt?: string;
  createdAt: string;
}

export interface InboundPaymentEvent {
  id: string;
  provider: string;
  providerTransactionId: string;
  virtualAccountNumber: string;
  bankBin: string;
  amount: number;
  currency: string;
  senderAccount?: string;
  senderBankBin?: string;
  senderName?: string;
  referenceContent?: string;
  status: 'RECEIVED' | 'MATCHED' | 'PROCESSED' | 'UNMATCHED' | 'MISMATCH' | 'DUPLICATE' | 'FAILED';
  errorMessage?: string;
  processedAt?: string;
  createdAt: string;
}

export interface SplitLegItem {
  id?: string;
  beneficiaryType: 'PLATFORM' | 'SELLER_INTERNAL' | 'SELLER_EXTERNAL';
  beneficiaryId?: string;
  accountId?: string;
  bankBin?: string;
  accountNumber?: string;
  beneficiaryName?: string;
  splitType: 'PERCENTAGE' | 'FIXED_AMOUNT' | 'REMAINDER';
  value: number;
  priority: number;
}

export interface SplitRule {
  id: string;
  organizationId: string;
  name: string;
  status: string;
  items: SplitLegItem[];
  createdAt: string;
}

export interface SettlementLeg {
  id: string;
  beneficiaryType: 'PLATFORM' | 'SELLER_INTERNAL' | 'SELLER_EXTERNAL';
  beneficiaryId?: string;
  accountId?: string;
  bankBin?: string;
  accountNumber?: string;
  beneficiaryName?: string;
  amount: number;
  currency: string;
  legType: 'INTERNAL_CREDIT' | 'EXTERNAL_PAYOUT' | 'COMMISSION';
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'RETRYING';
  payoutId?: string;
}

export interface Settlement {
  id: string;
  organizationId: string;
  collectionOrderId: string;
  grossAmount: number;
  platformCommission: number;
  sellerNetAmount: number;
  currency: string;
  status: 'PENDING' | 'FUNDS_RESERVED' | 'PROCESSING' | 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'RETRYING' | 'MANUAL_REVIEW' | 'REVERSED';
  ledgerJournalId?: string;
  failureReason?: string;
  legs: SettlementLeg[];
  createdAt: string;
  updatedAt: string;
}

export interface SettlementPreview {
  grossAmount: number;
  platformCommission: number;
  sellerNetAmount: number;
  legs: SettlementLeg[];
}

export interface MerchantCredential {
  id: string;
  keyId: string;
  secretKey?: string;
  name: string;
  status: string;
  expiresAt?: string;
  lastUsedAt?: string;
  createdAt: string;
}

export interface MerchantWebhookEndpoint {
  id: string;
  url: string;
  eventTypes?: string;
  secretKey?: string;
  status: string;
  createdAt: string;
}

export interface MerchantAccountConfig {
  id: string;
  organizationId: string;
  collectionAccountId: string;
  escrowAccountId: string;
  commissionAccountId: string;
  defaultCurrency: string;
  status: string;
  createdAt: string;
}

export interface BusinessDashboardSummary {
  totalVirtualAccounts: number;
  activeVirtualAccounts: number;
  pendingOrdersCount: number;
  paidOrdersCount: number;
  reviewOrdersCount: number;
  totalCollectedToday: number;
  totalSettledToday: number;
  pendingSettlementsCount: number;
  autoMatchRate: number;
}
