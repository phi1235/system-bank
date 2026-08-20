import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/api.model';
import {
  Account,
  AccountInquiryRequest,
  AccountInquiryResponse,
  AutoSweepOperation,
  AutoSweepProfile,
  SweepProduct,
  AdminCard,
  AdminTermDeposit,
  AuditLog,
  BatchApproveResult,
  BillCategoryItem,
  BillInquiryResult,
  BillPayResult,
  BillPaymentHistory,
  BillProviderItem,
  Card,
  CardReveal,
  BankItem,
  Beneficiary,
  CustomerProfile,
  DepositAdminSummary,
  DepositBatchResult,
  DepositProduct,
  DepositQuote,
  ForensicInvestigation,
  ForensicInvestigationDetail,
  ForensicTemporalState,
  ForensicVerificationRun,
  ForensicEvidenceExport,
  ForensicTwinFork,
  ForensicReplayRun,
  ForensicReplayScenario,
  ForensicCopilotSession,
  ForensicCopilotAnswer,
  ForensicCase,
  ForensicCaseDetail,
  ForensicCaseHistory,
  ForensicFinding,
  LedgerEntry,
  KycCase,
  NotificationItem,
  OutboxCounts,
  OutboxEvent,
  ReconRun,
  ReconRunDetail,
  RiskBlacklistEntry,
  RiskRule,
  SepayTopUpOrder,
  SepayTopUpRequest,
  SandboxConfigResponse,
  SandboxTopupRequest,
  SandboxTopupResponse,
  SupportTicket,
  TermDeposit,
  TopUpResponse,
  TransactionReport,
  Transfer,
  TransferDetail,
  TransferQuote,
  TransferRequest,
  BusinessOrganization,
  BusinessMember,
  BusinessMembership,
  VirtualAccount,
  CollectionOrder,
  InboundPaymentEvent,
  SplitRule,
  Settlement,
  SettlementPreview,
  MerchantCredential,
  MerchantWebhookEndpoint,
  MerchantAccountConfig,
  BusinessDashboardSummary,
} from '../models/domain.model';
import { ApiService } from './api.service';
import { DashboardKpis } from '../../features/admin/dashboard/dashboard.component';

@Injectable({ providedIn: 'root' })
export class BankApiService {
  private readonly api = inject(ApiService);

  getNotificationSandbox(params?: {
    q?: string;
    channel?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<NotificationItem>> {
    return this.api.post('/admin/notifications/sandbox/findSandboxByCondition', {
      q: params?.q,
      channel: params?.channel,
      page: params?.page,
      size: params?.size,
    });
  }

  // Notifications (customer inbox)
  /**
   * @param readFilter ALL | UNREAD | READ (default ALL)
   */
  myNotifications(
    page = 0,
    size = 20,
    readFilter: 'ALL' | 'UNREAD' | 'READ' = 'ALL',
  ): Observable<PageResponse<NotificationItem>> {
    return this.api.post('/notifications/findNotificationByCondition', {
      page,
      size,
      ...(readFilter && readFilter !== 'ALL' ? { readFilter } : {}),
    });
  }

  notificationUnreadCount(): Observable<{ unread: number }> {
    return this.api.get('/notifications/unread-count');
  }

  markNotificationRead(id: string): Observable<NotificationItem> {
    return this.api.post(`/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<{ updated: number }> {
    return this.api.post('/notifications/read-all', {});
  }

  // Notifications (admin ops shared inbox)
  adminOpsNotifications(page = 0, size = 20): Observable<PageResponse<NotificationItem>> {
    return this.api.post('/admin/notifications/findNotificationByCondition', { page, size });
  }

  adminOpsNotificationUnreadCount(): Observable<{ unread: number }> {
    return this.api.get('/admin/notifications/unread-count');
  }

  markAdminOpsNotificationRead(id: string): Observable<NotificationItem> {
    return this.api.post(`/admin/notifications/${id}/read`, {});
  }

  markAllAdminOpsNotificationsRead(): Observable<{ updated: number }> {
    return this.api.post('/admin/notifications/read-all', {});
  }

  // Customer profile
  getProfile(): Observable<CustomerProfile> {
    return this.api.get('/customers/me');
  }

  createProfile(body: {
    fullName: string;
    phone?: string;
    email?: string;
    nationalId?: string;
    address?: string;
  }): Observable<CustomerProfile> {
    return this.api.post('/customers/me', body);
  }

  updateProfile(body: {
    fullName?: string;
    phone?: string;
    email?: string;
    address?: string;
  }): Observable<CustomerProfile> {
    return this.api.put('/customers/me', body);
  }

  getMyKyc(): Observable<KycCase> {
    return this.api.get('/customers/me/kyc');
  }

  uploadMyKycDocument(documentType: string, file: File): Observable<KycCase> {
    const body = new FormData();
    body.append('documentType', documentType);
    body.append('file', file, file.name);
    return this.api.post('/customers/me/kyc/documents', body);
  }

  submitMyKyc(): Observable<KycCase> {
    return this.api.post('/customers/me/kyc/submit', {});
  }

  downloadMyKycDocument(id: string): Observable<Blob> {
    return this.api.getBlob(`/customers/me/kyc/documents/${encodeURIComponent(id)}/content`);
  }

  // Accounts
  listAccounts(): Observable<Account[]> {
    return this.api.get('/accounts');
  }

  openAccount(accountType = 'PAYMENT'): Observable<Account> {
    return this.api.post('/accounts', { accountType });
  }

  // Virtual debit cards
  myCards(): Observable<Card[]> {
    return this.api.get('/cards');
  }

  issueCard(accountId: string): Observable<Card> {
    return this.api.post(`/accounts/${accountId}/cards`, {});
  }

  cardAction(id: string, action: 'activate' | 'lock' | 'unlock' | 'close'): Observable<Card> {
    return this.api.post(`/cards/${id}/${action}`, {});
  }

  updateCardLimit(id: string, dailyLimit: number): Observable<Card> {
    return this.api.patch(`/cards/${id}/limits`, { dailyLimit });
  }

  /** Owner-only full PAN reveal (active card). */
  revealCard(id: string): Observable<CardReveal> {
    return this.api.post(`/cards/${id}/reveal`, {});
  }

  /** Staff card approval queue (default REQUESTED, oldest first). */
  adminCards(status = 'REQUESTED', page = 0, size = 20, q = ''): Observable<PageResponse<AdminCard>> {
    return this.api.post('/admin/cards/findCardByCondition', { status, page, size, q });
  }

  adminApproveCard(id: string): Observable<Card> {
    return this.api.post(`/admin/cards/${id}/approve`, {});
  }

  adminBatchApproveCards(ids: string[]): Observable<BatchApproveResult> {
    return this.api.post('/admin/cards/batch-approve', { ids });
  }

  adminRejectCard(id: string, reason: string): Observable<Card> {
    return this.api.post(`/admin/cards/${id}/reject`, { reason });
  }

  // Term deposits (so tiet kiem)
  depositProducts(): Observable<DepositProduct[]> {
    return this.api.get('/deposits/products');
  }

  depositQuote(productCode: string, amount: number): Observable<DepositQuote> {
    return this.api.get('/deposits/quote', { productCode, amount });
  }

  openDeposit(sourceAccountId: string, productCode: string, amount: number): Observable<TermDeposit> {
    return this.api.post('/deposits', { sourceAccountId, productCode, amount });
  }

  myDeposits(): Observable<TermDeposit[]> {
    return this.api.get('/deposits');
  }

  closeDeposit(id: string): Observable<TermDeposit> {
    return this.api.post(`/deposits/${id}/close`, {});
  }

  myAutoSweeps(): Observable<AutoSweepProfile[]> {
    return this.api.get('/deposits/auto-sweep');
  }

  autoSweepProducts(): Observable<SweepProduct[]> {
    return this.api.get('/deposits/auto-sweep/products');
  }

  saveAutoSweep(
    sourceAccountId: string,
    productCode: string,
    thresholdAmount: number,
    version?: number,
  ): Observable<AutoSweepProfile> {
    return this.api.put(`/deposits/auto-sweep/${sourceAccountId}`, {
      productCode,
      thresholdAmount,
      version,
    });
  }

  setAutoSweepEnabled(sourceAccountId: string, enabled: boolean): Observable<AutoSweepProfile> {
    return this.api.post(
      `/deposits/auto-sweep/${sourceAccountId}/${enabled ? 'resume' : 'pause'}`,
      {},
    );
  }

  autoSweepOperations(sourceAccountId: string, limit = 20): Observable<AutoSweepOperation[]> {
    return this.api.get(`/deposits/auto-sweep/${sourceAccountId}/operations`, { limit });
  }

  adminDepositSummary(): Observable<DepositAdminSummary> {
    return this.api.get('/admin/deposits/summary');
  }

  /** Manual accrual + maturity run (same job as the nightly scheduler). */
  adminRunDepositBatch(): Observable<DepositBatchResult> {
    return this.api.post('/admin/deposits/batch', {});
  }

  adminDeposits(
    page = 0,
    size = 20,
    filters?: {
      status?: string;
      productCode?: string;
      userId?: string;
      accountId?: string;
      accountNumber?: string;
      maturityFrom?: string;
      maturityTo?: string;
    },
  ): Observable<PageResponse<AdminTermDeposit>> {
    return this.api.post('/admin/deposits/findDepositByCondition', {
      page,
      size,
      status: filters?.status,
      productCode: filters?.productCode,
      userId: filters?.userId,
      accountId: filters?.accountId,
      accountNumber: filters?.accountNumber,
      maturityFrom: filters?.maturityFrom,
      maturityTo: filters?.maturityTo,
    });
  }

  adminAllDepositProducts(): Observable<DepositProduct[]> {
    return this.api.get('/admin/deposits/products');
  }

  /** Partial update; existing contracts keep their rate snapshots. */
  adminUpdateDepositProduct(
    code: string,
    body: { rateBps?: number; earlyRateBps?: number; minAmount?: number; active?: boolean },
  ): Observable<DepositProduct> {
    return this.api.patch(`/admin/deposits/products/${code}`, body);
  }

  getAccount(id: string): Observable<Account> {
    return this.api.get(`/accounts/${id}`);
  }

  accountStatement(
    accountId: string,
    params?: {
      page?: number;
      size?: number;
      entryType?: string;
      from?: string;
      to?: string;
    },
  ): Observable<PageResponse<LedgerEntry>> {
    return this.api.post(`/accounts/${accountId}/statement/findStatement`, {
      page: params?.page,
      size: params?.size,
      entryType: params?.entryType,
      from: params?.from,
      to: params?.to,
    });
  }

  exportAccountStatementCsv(
    accountId: string,
    params?: {
      entryType?: string;
      from?: string;
      to?: string;
    },
  ): Observable<Blob> {
    return this.api.getBlob(`/accounts/${accountId}/statement/export.csv`, {
      entryType: params?.entryType,
      from: params?.from,
      to: params?.to,
    });
  }

  getDashboardSummary(): Observable<DashboardKpis> {
    return this.api.get('/admin/dashboard/summary');
  }

  freezeAccount(id: string): Observable<Account> {
    return this.api.post(`/admin/accounts/${id}/freeze`, {});
  }

  unfreezeAccount(id: string): Observable<Account> {
    return this.api.post(`/admin/accounts/${id}/unfreeze`, {});
  }

  adminTopUp(
    accountId: string,
    body: { amount: number; description?: string },
  ): Observable<TopUpResponse> {
    return this.api.post(`/admin/accounts/${accountId}/top-up`, body);
  }

  /** Staff list/search: q = account number | account UUID | owner user UUID */
  adminListAccounts(
    page = 0,
    size = 20,
    q?: string,
    status?: string,
    accountType?: string,
  ): Observable<PageResponse<Account>> {
    return this.api.post('/admin/accounts/findAccountByCondition', { page, size, q, status, accountType });
  }

  adminAccountDetail(id: string): Observable<Account> {
    return this.api.get(`/admin/accounts/${id}`);
  }

  // Banks & Account Inquiry
  listBanks(): Observable<BankItem[]> {
    return this.api.get('/transactions/banks');
  }

  accountInquiry(body: AccountInquiryRequest): Observable<AccountInquiryResponse> {
    return this.api.post('/beneficiary-inquiries', body);
  }

  // Transfers
  transfer(body: TransferRequest, idempotencyKey: string): Observable<Transfer> {
    return this.api.post('/transactions/transfers', body, {
      'Idempotency-Key': idempotencyKey,
    });
  }

  myTransfers(
    page = 0,
    size = 20,
    params?: {
      status?: string;
      from?: string;
      to?: string;
    },
  ): Observable<PageResponse<Transfer>> {
    return this.api.post('/transactions/transfers/findTransferLog', {
      page,
      size,
      status: params?.status,
      from: params?.from,
      to: params?.to,
    });
  }

  getTransfer(id: string): Observable<Transfer> {
    return this.api.get(`/transactions/transfers/${id}`);
  }

  /** Transfer order + ordered saga steps (owner or staff). */
  getTransferDetail(id: string): Observable<TransferDetail> {
    return this.api.get(`/transactions/transfers/${id}/detail`);
  }

  /** Fee + remaining daily limit preview (no order created). */
  transferQuote(amount?: number): Observable<TransferQuote> {
    return this.api.get('/transactions/transfers/quote', {
      amount: amount != null && amount > 0 ? amount : undefined,
    });
  }

  // Beneficiaries (internal transfer address book)
  listBeneficiaries(): Observable<Beneficiary[]> {
    return this.api.get('/transactions/beneficiaries');
  }

  createBeneficiary(body: { nickname: string; accountNumber: string }): Observable<Beneficiary> {
    return this.api.post('/transactions/beneficiaries', body);
  }

  renameBeneficiary(id: string, nickname: string): Observable<Beneficiary> {
    return this.api.put(`/transactions/beneficiaries/${id}`, { nickname });
  }

  deleteBeneficiary(id: string): Observable<void> {
    return this.api.delete(`/transactions/beneficiaries/${id}`);
  }

  // Support tickets (customer)
  createSupportTicket(body: {
    category: string;
    subject: string;
    body: string;
    priority?: string;
  }): Observable<SupportTicket> {
    return this.api.post('/customers/me/support-tickets', body);
  }

  mySupportTickets(page = 0, size = 20): Observable<PageResponse<SupportTicket>> {
    return this.api.post('/customers/me/support-tickets/findSupportTicketByCondition', { page, size });
  }

  mySupportTicket(id: string): Observable<SupportTicket> {
    return this.api.get(`/customers/me/support-tickets/${encodeURIComponent(id)}`);
  }

  // Support tickets (admin)
  adminSupportTickets(
    page = 0,
    size = 20,
    filters?: { status?: string; category?: string; q?: string },
  ): Observable<PageResponse<SupportTicket>> {
    return this.api.post('/admin/support-tickets/findSupportTicketByCondition', {
      page,
      size,
      status: filters?.status,
      category: filters?.category,
      q: filters?.q,
    });
  }

  adminSupportTicket(id: string): Observable<SupportTicket> {
    return this.api.get(`/admin/support-tickets/${encodeURIComponent(id)}`);
  }

  claimSupportTicket(id: string): Observable<SupportTicket> {
    return this.api.post(`/admin/support-tickets/${encodeURIComponent(id)}/claim`, {});
  }

  resolveSupportTicket(id: string, resolutionNote?: string): Observable<SupportTicket> {
    return this.api.post(`/admin/support-tickets/${encodeURIComponent(id)}/resolve`, {
      resolutionNote: resolutionNote || undefined,
    });
  }

  rejectSupportTicket(id: string, reason: string): Observable<SupportTicket> {
    return this.api.post(`/admin/support-tickets/${encodeURIComponent(id)}/reject`, { reason });
  }

  requestSupportTicketInfo(id: string, message: string): Observable<SupportTicket> {
    return this.api.post(`/admin/support-tickets/${encodeURIComponent(id)}/request-info`, {
      message,
    });
  }

  postAdminSupportTicketMessage(id: string, body: string): Observable<SupportTicket> {
    return this.api.post(`/admin/support-tickets/${encodeURIComponent(id)}/messages`, { body });
  }

  postMySupportTicketMessage(id: string, body: string): Observable<SupportTicket> {
    return this.api.post(`/customers/me/support-tickets/${encodeURIComponent(id)}/messages`, {
      body,
    });
  }

  // Admin
  listCustomers(
    page = 0,
    size = 20,
    q?: string,
    kycStatus?: string,
  ): Observable<PageResponse<CustomerProfile>> {
    return this.api.post('/admin/customers/findCustomerByCondition', { page, size, q, kycStatus });
  }

  getAdminKyc(customerId: string): Observable<KycCase> {
    return this.api.get(`/admin/kyc/customers/${encodeURIComponent(customerId)}`);
  }

  riskRules(page = 0, size = 20): Observable<PageResponse<RiskRule>> {
    return this.api.get('/admin/risk/rules', { page, size });
  }

  createRiskRule(body: Omit<RiskRule, 'id' | 'createdAt' | 'updatedAt'>): Observable<RiskRule> {
    return this.api.post('/admin/risk/rules', body);
  }

  updateRiskRule(
    id: string,
    body: Omit<RiskRule, 'id' | 'createdAt' | 'updatedAt'>,
  ): Observable<RiskRule> {
    return this.api.put(`/admin/risk/rules/${encodeURIComponent(id)}`, body);
  }

  riskBlacklist(page = 0, size = 20): Observable<PageResponse<RiskBlacklistEntry>> {
    return this.api.get('/admin/risk/blacklist', { page, size });
  }

  addRiskBlacklist(body: {
    subjectType: string;
    subjectValue: string;
    reason: string;
    expiresAt?: string | null;
  }): Observable<RiskBlacklistEntry> {
    return this.api.post('/admin/risk/blacklist', body);
  }

  deactivateRiskBlacklist(id: string): Observable<RiskBlacklistEntry> {
    return this.api.post(`/admin/risk/blacklist/${encodeURIComponent(id)}/deactivate`, {});
  }

  decideRiskTransfer(id: string, decision: 'approve' | 'reject', note: string): Observable<Transfer> {
    return this.api.post(`/admin/risk/transfers/${encodeURIComponent(id)}/${decision}`, { note });
  }

  decideAdminKyc(caseId: string, decision: 'APPROVE' | 'REJECT', reason?: string): Observable<KycCase> {
    return this.api.post(`/admin/kyc/cases/${encodeURIComponent(caseId)}/decision`, {
      decision,
      reason: reason || undefined,
    });
  }

  downloadAdminKycDocument(id: string): Observable<Blob> {
    return this.api.getBlob(`/admin/kyc/documents/${encodeURIComponent(id)}/content`);
  }

  adminTransfers(
    page = 0,
    size = 20,
    filters?: {
      status?: string;
      transferId?: string;
      q?: string;
      from?: string;
      to?: string;
    },
  ): Observable<PageResponse<Transfer>> {
    return this.api.post('/admin/transfers/findTransferByCondition', {
      page,
      size,
      status: filters?.status,
      transferId: filters?.transferId,
      q: filters?.q,
      from: filters?.from,
      to: filters?.to,
    });
  }

  forensicInvestigations(
    page = 0,
    size = 20,
    filters?: {
      q?: string;
      transactionId?: string;
      transferStatus?: string;
      riskDecision?: string;
      from?: string;
      to?: string;
    },
  ): Observable<PageResponse<ForensicInvestigation>> {
    return this.api.post('/admin/forensics/investigations/findByCondition', {
      page,
      size,
      ...filters,
    });
  }

  forensicsCapabilities(): Observable<{ enabled: boolean }> {
    return this.api.get('/admin/forensics/capabilities');
  }

  forensicInvestigation(transactionId: string): Observable<ForensicInvestigationDetail> {
    return this.api.get(
      `/admin/forensics/investigations/${encodeURIComponent(transactionId)}`,
    );
  }

  forensicTemporalState(transactionId: string, at: string): Observable<ForensicTemporalState> {
    return this.api.get(
      `/admin/forensics/investigations/${encodeURIComponent(transactionId)}/temporal-state`,
      { at },
    );
  }

  runForensicVerification(
    transactionId: string,
    idempotencyKey: string,
  ): Observable<ForensicVerificationRun> {
    return this.api.post(
      `/admin/forensics/verification/check/${encodeURIComponent(transactionId)}`,
      {},
      { 'Idempotency-Key': idempotencyKey },
    );
  }

  createForensicExport(caseId: string, reason: string): Observable<ForensicEvidenceExport> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(caseId)}/exports`, { reason });
  }

  forensicExport(jobId: string): Observable<ForensicEvidenceExport> {
    return this.api.get(`/admin/forensics/exports/${encodeURIComponent(jobId)}`);
  }

  downloadForensicExport(jobId: string): Observable<Blob> {
    return this.api.getBlob(`/admin/forensics/exports/${encodeURIComponent(jobId)}/download`);
  }

  createForensicFork(transactionId: string, ttlMinutes = 60): Observable<ForensicTwinFork> {
    return this.api.post('/admin/forensics/twin/forks', { transactionId, ttlMinutes });
  }

  createForensicReplay(
    idempotencyKey: string,
    body: { forkId: string; scenarioId: string; seed: number; targetCommitSha: string },
  ): Observable<ForensicReplayRun> {
    return this.api.post('/admin/forensics/twin/replays', body, { 'Idempotency-Key': idempotencyKey });
  }

  forensicReplay(runId: string): Observable<ForensicReplayRun> {
    return this.api.get(`/admin/forensics/twin/runs/${encodeURIComponent(runId)}`);
  }

  forensicReplayResult(runId: string): Observable<Blob> {
    return this.api.getBlob(`/admin/forensics/twin/runs/${encodeURIComponent(runId)}/result`);
  }

  deleteForensicFork(forkId: string): Observable<void> {
    return this.api.delete(`/admin/forensics/twin/forks/${encodeURIComponent(forkId)}`);
  }

  forensicReplayScenarios(all = false): Observable<ForensicReplayScenario[]> {
    return this.api.get(`/admin/forensics/scenarios${all ? '/all' : ''}`);
  }

  forensicReplayScenarioEngines(): Observable<string[]> {
    return this.api.get('/admin/forensics/scenarios/engines');
  }

  forensicReplayScenarioFaultTypes(): Observable<string[]> {
    return this.api.get('/admin/forensics/scenarios/fault-types');
  }

  createForensicReplayScenario(body: {
    scenarioId: string; title: string; engineKey: string; sourceIncidentId: string;
    sourceEvidenceRef: string; definition: Record<string, unknown>; sanitized: boolean;
  }): Observable<ForensicReplayScenario> {
    return this.api.post('/admin/forensics/scenarios', body);
  }

  confirmForensicReplayScenario(id: string, expectedVersion: number): Observable<ForensicReplayScenario> {
    return this.api.post(`/admin/forensics/scenarios/${encodeURIComponent(id)}/confirm`, { expectedVersion });
  }

  createForensicCopilotSession(transactionId?: string | null, caseId?: string | null): Observable<ForensicCopilotSession> {
    const payload: { transactionId?: string; caseId?: string } = {};
    if (transactionId) payload.transactionId = transactionId;
    if (caseId) payload.caseId = caseId;
    return this.api.post('/admin/forensics/copilot/sessions', payload);
  }

  askForensicCopilot(sessionId: string, question: string): Observable<ForensicCopilotAnswer> {
    return this.api.post(
      `/admin/forensics/copilot/sessions/${encodeURIComponent(sessionId)}/messages`,
      { question },
    );
  }

  forensicCases(
    page = 0,
    size = 20,
    filters?: {
      q?: string;
      status?: string;
      priority?: string;
      assignedTo?: string;
      transactionId?: string;
    },
  ): Observable<PageResponse<ForensicCase>> {
    return this.api.post('/admin/forensics/cases/findByCondition', { page, size, ...filters });
  }

  forensicCase(id: string): Observable<ForensicCaseDetail> {
    return this.api.get(`/admin/forensics/cases/${encodeURIComponent(id)}`);
  }

  createForensicCase(body: {
    transactionId?: string;
    accountId?: string;
    sourceType: string;
    sourceReferenceId?: string;
    priority: string;
    title: string;
    summary?: string;
  }): Observable<ForensicCase> {
    return this.api.post('/admin/forensics/cases', body);
  }

  assignForensicCase(id: string, assignee: string, expectedVersion: number, note?: string): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/assign`, {
      assignee, expectedVersion, note,
    });
  }

  startForensicCase(id: string, expectedVersion: number): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/start`, { expectedVersion });
  }

  confirmForensicRootCause(id: string, expectedVersion: number, note?: string): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/confirm-root-cause`, {
      expectedVersion, note,
    });
  }

  verifyForensicReplay(id: string, expectedVersion: number, replayRunId?: string, note?: string): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/verify-replay`, {
      expectedVersion, replayRunId, note,
    });
  }

  submitForensicCase(id: string, expectedVersion: number, recommendation: string): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/submit`, {
      expectedVersion, recommendation,
    });
  }

  approveForensicCase(
    id: string,
    expectedVersion: number,
    resolutionCode: string,
    resolutionNote: string,
    systemic = false,
  ): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/approve-resolution`, {
      expectedVersion, resolutionCode, resolutionNote, systemic,
    });
  }

  rejectForensicCase(id: string, expectedVersion: number, reason: string): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/reject-resolution`, {
      expectedVersion, reason,
    });
  }

  reopenForensicCase(id: string, expectedVersion: number, reason: string): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/reopen`, {
      expectedVersion, reason,
    });
  }

  addForensicFinding(id: string, body: {
    ruleCode: string;
    severity: string;
    title: string;
    detail?: string;
    evidence?: Record<string, unknown>;
  }): Observable<ForensicFinding> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/findings`, body);
  }

  forensicCaseHistory(id: string, page = 0, size = 50): Observable<PageResponse<ForensicCaseHistory>> {
    return this.api.get(`/admin/forensics/cases/${encodeURIComponent(id)}/history`, { page, size });
  }

  recordForensicRemediation(
    id: string,
    expectedVersion: number,
    actionType: string,
    description: string,
    referenceId?: string,
    completed = false,
  ): Observable<ForensicCase> {
    return this.api.post(`/admin/forensics/cases/${encodeURIComponent(id)}/remediation`, {
      expectedVersion, actionType, description, referenceId, completed,
    });
  }

  forensicCausalGraph(transactionId: string): Observable<unknown> {
    return this.api.get(`/admin/forensics/causal-graph/${encodeURIComponent(transactionId)}`);
  }

  executeRemediationAdjustment(
    body: { transactionId?: string; caseId?: string; amount: number; reason: string },
    idempotencyKey?: string,
  ): Observable<Record<string, unknown>> {
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined;
    return this.api.post<Record<string, unknown>>('/admin/forensics/remediation/adjustment', body, headers);
  }

  executeRemediationHold(
    body: { targetAccountId?: string; caseId?: string; amount: number; reason: string },
    idempotencyKey?: string,
  ): Observable<Record<string, unknown>> {
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined;
    return this.api.post<Record<string, unknown>>('/admin/forensics/remediation/hold', body, headers);
  }

  forensicViolations(
    page = 0,
    size = 20,
    filters?: { disposition?: string; severity?: string; ruleCode?: string; transactionId?: string },
  ): Observable<PageResponse<ForensicFinding>> {
    return this.api.get('/admin/forensics/violations', { page, size, ...filters });
  }

  acknowledgeForensicViolation(id: string, expectedVersion: number, note: string): Observable<ForensicFinding> {
    return this.api.post(`/admin/forensics/violations/${encodeURIComponent(id)}/acknowledge`, {
      expectedVersion,
      note,
    });
  }

  resolveForensicViolation(
    id: string,
    expectedVersion: number,
    reason: string,
    evidence: Record<string, unknown>,
  ): Observable<ForensicFinding> {
    return this.api.post(`/admin/forensics/violations/${encodeURIComponent(id)}/resolve`, {
      expectedVersion,
      reason,
      evidence,
    });
  }

  adminTransfersExportChunks(
    page = 0,
    size = 2000,
    filters?: {
      status?: string;
      transferId?: string;
      q?: string;
      from?: string;
      to?: string;
      lastCreatedAt?: string;
    },
  ): Observable<Transfer[]> {
    return this.api.post<Transfer[]>('/admin/transfers/findTransferByCondition', {
      page,
      size,
      noCount: true,
      status: filters?.status,
      transferId: filters?.transferId,
      q: filters?.q,
      from: filters?.from,
      to: filters?.to,
      lastCreatedAt: filters?.lastCreatedAt,
    });
  }

  /** Aggregated transfer report; dates as yyyy-MM-dd banking days (defaults: last 30 days). */
  adminTransactionReport(filters?: {
    from?: string;
    to?: string;
    accountId?: string;
    top?: number;
  }): Observable<TransactionReport> {
    return this.api.get('/admin/transactions/reports', {
      from: filters?.from,
      to: filters?.to,
      accountId: filters?.accountId,
      top: filters?.top,
    });
  }

  downloadTransactionReportCsv(filters?: {
    from?: string;
    to?: string;
    accountId?: string;
  }): Observable<Blob> {
    return this.api.getBlob('/admin/transactions/reports/export-csv', {
      from: filters?.from,
      to: filters?.to,
      accountId: filters?.accountId,
    });
  }

  adminReconRuns(page = 0, size = 20): Observable<PageResponse<ReconRun>> {
    return this.api.post('/admin/recon/runs/findReconRunsByCondition', { page, size });
  }

  adminReconRun(id: string): Observable<ReconRunDetail> {
    return this.api.get(`/admin/recon/runs/${id}`);
  }

  /** Manual reconciliation run for one banking date (yyyy-MM-dd). */
  adminRunRecon(date: string): Observable<ReconRun> {
    return this.api.post('/admin/recon/runs', { date });
  }

  /**
   * Staff audit log list with optional filters.
   * Dates as ISO-8601 Instant when provided.
   */
  auditLogs(
    page = 0,
    size = 20,
    filters?: {
      action?: string;
      resourceType?: string;
      actorUserId?: string;
      resourceId?: string;
      from?: string;
      to?: string;
      noCount?: boolean;
    },
  ): Observable<PageResponse<AuditLog>> {
    return this.api.post('/admin/audit-logs/findAuditLogByCondition', {
      page,
      size,
      noCount: filters?.noCount,
      action: filters?.action,
      resourceType: filters?.resourceType,
      actorUserId: filters?.actorUserId,
      resourceId: filters?.resourceId,
      from: filters?.from,
      to: filters?.to,
    });
  }

  auditLogDetail(id: string): Observable<AuditLog> {
    return this.api.get(`/admin/audit-logs/${id}`);
  }

  /** Staff outbox inspect/replay (default status DEAD on API). */
  adminOutboxList(
    page = 0,
    size = 20,
    filters?: {
      status?: string;
      eventType?: string;
      eventId?: string;
      aggregateId?: string;
      q?: string;
      from?: string;
      to?: string;
    },
  ): Observable<PageResponse<OutboxEvent>> {
    return this.api.post('/admin/outbox/findOutboxByCondition', {
      page,
      size,
      status: filters?.status,
      eventType: filters?.eventType,
      eventId: filters?.eventId,
      aggregateId: filters?.aggregateId,
      q: filters?.q,
      from: filters?.from,
      to: filters?.to,
    });
  }

  adminOutboxCounts(): Observable<OutboxCounts> {
    return this.api.get('/admin/outbox/counts');
  }

  adminOutboxDetail(id: string): Observable<OutboxEvent> {
    return this.api.get(`/admin/outbox/${id}`);
  }

  adminOutboxReplay(id: string): Observable<OutboxEvent> {
    return this.api.post(`/admin/outbox/${id}/replay`, {});
  }

  // RBAC
  rbacMatrix(): Observable<RbacMatrix> {
    return this.api.get('/admin/rbac/matrix');
  }

  rbacRoles(staffOnly = true): Observable<RbacRole[]> {
    return this.api.get('/admin/rbac/roles', { staffOnly });
  }

  rbacPermissions(): Observable<RbacPermission[]> {
    return this.api.get('/admin/rbac/permissions');
  }

  createRole(body: {
    code: string;
    name: string;
    description?: string;
    staff: boolean;
    permissions: string[];
  }): Observable<RbacRole> {
    return this.api.post('/admin/rbac/roles', body);
  }

  updateRole(
    code: string,
    body: { name: string; description?: string; staff?: boolean },
  ): Observable<RbacRole> {
    return this.api.put(`/admin/rbac/roles/${code}`, body);
  }

  updateRolePermissions(code: string, permissions: string[]): Observable<RbacRole> {
    return this.api.put(`/admin/rbac/roles/${code}/permissions`, { permissions });
  }

  rbacUsers(
    page = 0,
    size = 20,
    filters?: { q?: string; enabled?: boolean; userId?: string },
  ): Observable<PageResponse<RbacStaffUser>> {
    return this.api.post('/admin/rbac/users/findUserByCondition', {
      page,
      size,
      q: filters?.q,
      enabled: filters?.enabled,
      userId: filters?.userId,
    });
  }

  rbacUserDetail(userId: string): Observable<RbacStaffUser> {
    return this.api.get(`/admin/rbac/users/${userId}`);
  }

  assignRoles(userId: string, roles: string[]): Observable<RbacStaffUser> {
    return this.api.put(`/admin/rbac/users/${userId}/roles`, { roles });
  }

  // ── Bill Payments ──

  billCategories(): Observable<BillCategoryItem[]> {
    return this.api.get('/bills/categories');
  }

  billProviders(categoryId?: string): Observable<BillProviderItem[]> {
    return this.api.get('/bills/providers', categoryId ? { categoryId } : {});
  }

  billInquiry(providerId: string, customerCode: string): Observable<BillInquiryResult> {
    return this.api.post('/bills/inquiry', { providerId, customerCode });
  }

  billPay(providerId: string, customerCode: string, amount: number): Observable<BillPayResult> {
    return this.api.post('/bills/pay', { providerId, customerCode, amount });
  }

  billHistory(page = 0, size = 20): Observable<PageResponse<BillPaymentHistory>> {
    return this.api.post('/bills/history/findBillPaymentHistory', { page, size });
  }

  // ── SePay VietQR Top-Up ──

  createSepayTopUp(body: SepayTopUpRequest): Observable<SepayTopUpOrder> {
    return this.api.post<SepayTopUpOrder>('/payments/sepay/topup', body);
  }

  getSepayOrder(orderCode: string): Observable<SepayTopUpOrder> {
    return this.api.get<SepayTopUpOrder>(`/payments/sepay/orders/${orderCode}`);
  }

  getMySepayOrders(): Observable<SepayTopUpOrder[]> {
    return this.api.get<SepayTopUpOrder[]>('/payments/sepay/my-orders');
  }

  // ── Sandbox 1-Click Top-Up ──

  getSandboxConfig(): Observable<SandboxConfigResponse> {
    return this.api.get<SandboxConfigResponse>('/sandbox/config');
  }

  sandboxTopup(body: SandboxTopupRequest): Observable<SandboxTopupResponse> {
    return this.api.post<SandboxTopupResponse>('/sandbox/topup', body);
  }

  // ── Admin Transfer Reconciliation & Manual Action ──

  listManualReviewTransfers(page = 0, size = 20): Observable<PageResponse<Transfer>> {
    return this.api.get<PageResponse<Transfer>>('/admin/transfers/manual-review', { page, size });
  }

  forceSettleTransfer(id: string, reason: string): Observable<Transfer> {
    return this.api.post<Transfer>(`/admin/transfers/${encodeURIComponent(id)}/force-settle`, { reason });
  }

  forceRefundTransfer(id: string, reason: string): Observable<Transfer> {
    return this.api.post<Transfer>(`/admin/transfers/${encodeURIComponent(id)}/force-refund`, { reason });
  }

  // ── B2B Organizations & Members (Auth Service via Gateway) ──

  listMyOrganizations(): Observable<BusinessOrganization[]> {
    return this.api.get<BusinessOrganization[]>('/businesses');
  }

  registerOrganization(body: { code: string; name: string; taxCode?: string }): Observable<BusinessOrganization> {
    return this.api.post<BusinessOrganization>('/businesses', body);
  }

  listBusinessMembers(orgId: string): Observable<BusinessMember[]> {
    return this.api.get<BusinessMember[]>(`/businesses/${encodeURIComponent(orgId)}/members`);
  }

  addBusinessMember(orgId: string, body: { userId: string; role: string }): Observable<BusinessMember> {
    return this.api.post<BusinessMember>(`/businesses/${encodeURIComponent(orgId)}/members`, body);
  }

  removeBusinessMember(orgId: string, memberId: string): Observable<void> {
    return this.api.delete<void>(`/businesses/${encodeURIComponent(orgId)}/members/${encodeURIComponent(memberId)}`);
  }

  getMyBusinessMembership(orgId: string): Observable<BusinessMembership> {
    return this.api.get<BusinessMembership>(`/businesses/${encodeURIComponent(orgId)}/my-membership`);
  }

  // ── B2B Dashboard ──

  getBusinessDashboardSummary(orgId: string): Observable<BusinessDashboardSummary> {
    return this.api.get<BusinessDashboardSummary>(`/businesses/${encodeURIComponent(orgId)}/dashboard/summary`);
  }

  // ── B2B Virtual Accounts ──

  listVirtualAccounts(orgId: string, params?: { q?: string; status?: string; page?: number; size?: number }): Observable<PageResponse<VirtualAccount>> {
    return this.api.get<PageResponse<VirtualAccount>>(`/businesses/${encodeURIComponent(orgId)}/virtual-accounts`, params);
  }

  provisionVirtualAccount(orgId: string, body: {
    provider?: string;
    bankBin?: string;
    parentAccountId?: string;
    mode: string;
    customerReference?: string;
    expiresAt?: string;
  }): Observable<VirtualAccount> {
    return this.api.post<VirtualAccount>(`/businesses/${encodeURIComponent(orgId)}/virtual-accounts`, body);
  }

  getVirtualAccount(orgId: string, id: string): Observable<VirtualAccount> {
    return this.api.get<VirtualAccount>(`/businesses/${encodeURIComponent(orgId)}/virtual-accounts/${encodeURIComponent(id)}`);
  }

  closeVirtualAccount(orgId: string, id: string): Observable<void> {
    return this.api.post<void>(`/businesses/${encodeURIComponent(orgId)}/virtual-accounts/${encodeURIComponent(id)}/close`, {});
  }

  // ── B2B Collection Orders ──

  listCollectionOrders(orgId: string, params?: { q?: string; status?: string; page?: number; size?: number }): Observable<PageResponse<CollectionOrder>> {
    return this.api.get<PageResponse<CollectionOrder>>(`/businesses/${encodeURIComponent(orgId)}/collection-orders`, params);
  }

  createCollectionOrder(orgId: string, body: {
    merchantOrderId: string;
    virtualAccountId?: string;
    vaMode?: string;
    expectedAmount: number;
    currency?: string;
    customerReference?: string;
    splitRuleId?: string;
    splitLegs?: any[];
    expiresAt?: string;
  }, idempotencyKey?: string): Observable<CollectionOrder> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return this.api.post<CollectionOrder>(`/businesses/${encodeURIComponent(orgId)}/collection-orders`, body, headers);
  }

  getCollectionOrder(orgId: string, id: string): Observable<CollectionOrder> {
    return this.api.get<CollectionOrder>(`/businesses/${encodeURIComponent(orgId)}/collection-orders/${encodeURIComponent(id)}`);
  }

  cancelCollectionOrder(orgId: string, id: string): Observable<void> {
    return this.api.post<void>(`/businesses/${encodeURIComponent(orgId)}/collection-orders/${encodeURIComponent(id)}/cancel`, {});
  }

  completeCollectionOrder(orgId: string, id: string): Observable<Settlement> {
    return this.api.post<Settlement>(`/businesses/${encodeURIComponent(orgId)}/collection-orders/${encodeURIComponent(id)}/complete`, {});
  }

  // ── B2B Split Rules ──

  listSplitRules(orgId: string): Observable<SplitRule[]> {
    return this.api.get<SplitRule[]>(`/businesses/${encodeURIComponent(orgId)}/split-rules`);
  }

  createSplitRule(orgId: string, body: { name: string; items: any[] }): Observable<SplitRule> {
    return this.api.post<SplitRule>(`/businesses/${encodeURIComponent(orgId)}/split-rules`, body);
  }

  deleteSplitRule(orgId: string, ruleId: string): Observable<void> {
    return this.api.delete<void>(`/businesses/${encodeURIComponent(orgId)}/split-rules/${encodeURIComponent(ruleId)}`);
  }

  // ── B2B Settlements ──

  listSettlements(orgId: string, params?: { status?: string; page?: number; size?: number }): Observable<PageResponse<Settlement>> {
    return this.api.get<PageResponse<Settlement>>(`/businesses/${encodeURIComponent(orgId)}/settlements`, params);
  }

  getSettlement(orgId: string, id: string): Observable<Settlement> {
    return this.api.get<Settlement>(`/businesses/${encodeURIComponent(orgId)}/settlements/${encodeURIComponent(id)}`);
  }

  retrySettlement(orgId: string, id: string): Observable<Settlement> {
    return this.api.post<Settlement>(`/businesses/${encodeURIComponent(orgId)}/settlements/${encodeURIComponent(id)}/retry`, {});
  }

  previewSettlement(orgId: string, body: { grossAmount: number; splitRuleId?: string; customLegs?: any[] }): Observable<SettlementPreview> {
    return this.api.post<SettlementPreview>(`/businesses/${encodeURIComponent(orgId)}/settlements/preview`, body);
  }

  // ── B2B Merchant Developer (Credentials & Webhooks) ──

  getMerchantAccountConfig(orgId: string): Observable<MerchantAccountConfig> {
    return this.api.get<MerchantAccountConfig>(`/businesses/${encodeURIComponent(orgId)}/merchant-account`);
  }

  configureMerchantAccount(orgId: string, body: { collectionAccountId: string; escrowAccountId: string; defaultCurrency?: string }): Observable<MerchantAccountConfig> {
    return this.api.post<MerchantAccountConfig>(`/businesses/${encodeURIComponent(orgId)}/merchant-account`, body);
  }

  listMerchantCredentials(orgId: string): Observable<MerchantCredential[]> {
    return this.api.get<MerchantCredential[]>(`/businesses/${encodeURIComponent(orgId)}/credentials`);
  }

  createMerchantCredential(orgId: string, body: { name: string; expiresAt?: string }): Observable<MerchantCredential> {
    return this.api.post<MerchantCredential>(`/businesses/${encodeURIComponent(orgId)}/credentials`, body);
  }

  deleteMerchantCredential(orgId: string, id: string): Observable<void> {
    return this.api.delete<void>(`/businesses/${encodeURIComponent(orgId)}/credentials/${encodeURIComponent(id)}`);
  }

  listMerchantWebhooks(orgId: string): Observable<MerchantWebhookEndpoint[]> {
    return this.api.get<MerchantWebhookEndpoint[]>(`/businesses/${encodeURIComponent(orgId)}/webhook-endpoints`);
  }

  registerMerchantWebhook(orgId: string, body: { url: string; eventTypes?: string }): Observable<MerchantWebhookEndpoint> {
    return this.api.post<MerchantWebhookEndpoint>(`/businesses/${encodeURIComponent(orgId)}/webhook-endpoints`, body);
  }

  deleteMerchantWebhook(orgId: string, id: string): Observable<void> {
    return this.api.delete<void>(`/businesses/${encodeURIComponent(orgId)}/webhook-endpoints/${encodeURIComponent(id)}`);
  }

  // ── Back-Office Admin Operations ──

  adminSearchVirtualAccounts(params?: { organizationId?: string; q?: string; status?: string; page?: number; size?: number }): Observable<PageResponse<VirtualAccount>> {
    return this.api.get<PageResponse<VirtualAccount>>('/admin/virtual-accounts', params);
  }

  adminSearchInboundEvents(params?: { provider?: string; q?: string; status?: string; page?: number; size?: number }): Observable<PageResponse<InboundPaymentEvent>> {
    return this.api.get<PageResponse<InboundPaymentEvent>>('/admin/virtual-accounts/inbound-events', params);
  }

  adminSearchCollectionOrders(params?: { organizationId?: string; q?: string; status?: string; page?: number; size?: number }): Observable<PageResponse<CollectionOrder>> {
    return this.api.get<PageResponse<CollectionOrder>>('/admin/collection-orders', params);
  }

  adminCompleteCollectionOrder(id: string): Observable<Settlement> {
    return this.api.post<Settlement>(`/admin/collection-orders/${encodeURIComponent(id)}/complete`, {});
  }

  adminSearchSettlements(params?: { organizationId?: string; status?: string; page?: number; size?: number }): Observable<PageResponse<Settlement>> {
    return this.api.get<PageResponse<Settlement>>('/admin/settlements', params);
  }

  adminRetrySettlement(id: string): Observable<Settlement> {
    return this.api.post<Settlement>(`/admin/settlements/${encodeURIComponent(id)}/retry`, {});
  }
}

export interface RbacRole {
  code: string;
  name: string;
  description: string;
  staff: boolean;
  permissions: string[];
}

export interface RbacPermission {
  code: string;
  description: string;
}

export interface RbacMatrixCell {
  roleCode: string;
  permissionCode: string;
  granted: boolean;
}

export interface RbacMatrix {
  roles: RbacRole[];
  permissions: RbacPermission[];
  cells: RbacMatrixCell[];
}

export interface RbacStaffUser {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  permissions: string[];
  staff: boolean;
  enabled: boolean;
  mustChangePassword?: boolean;
  lockedReason?: string | null;
  openResetTicket?: boolean;
  createdAt?: string | null;
}

// ── Bill Payment API Methods (added to BankApiService class above) ──
// These are injected into the class via the partial below.
