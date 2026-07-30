import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/api.model';
import {
  Account,
  AccountInquiryRequest,
  AccountInquiryResponse,
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
  LedgerEntry,
  NotificationItem,
  OutboxCounts,
  OutboxEvent,
  ReconRun,
  ReconRunDetail,
  SupportTicket,
  TermDeposit,
  TopUpResponse,
  TransactionReport,
  Transfer,
  TransferDetail,
  TransferQuote,
  TransferRequest,
} from '../models/domain.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class BankApiService {
  private readonly api = inject(ApiService);

  getNotificationSandbox(params?: {
    q?: string;
    channel?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<NotificationItem>> {
    return this.api.get('/admin/notifications/sandbox', {
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
    return this.api.get('/notifications', {
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
    return this.api.get('/admin/notifications', { page, size });
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
    return this.api.get('/admin/cards', { status, page, size, q });
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
    return this.api.get('/admin/deposits', {
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
    return this.api.get(`/accounts/${accountId}/statement`, {
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
    return this.api.get('/admin/accounts', { page, size, q, status, accountType });
  }

  adminAccountDetail(id: string): Observable<Account> {
    return this.api.get(`/admin/accounts/${id}`);
  }

  // Banks & Account Inquiry
  listBanks(): Observable<BankItem[]> {
    return this.api.get('/transactions/banks');
  }

  accountInquiry(body: AccountInquiryRequest): Observable<AccountInquiryResponse> {
    return this.api.post('/transactions/account-inquiry', body);
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
    return this.api.get('/transactions/transfers', {
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
    return this.api.get('/customers/me/support-tickets', { page, size });
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
    return this.api.get('/admin/support-tickets', {
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
    return this.api.get('/admin/customers', { page, size, q, kycStatus });
  }

  updateKyc(id: string, kycStatus: string): Observable<CustomerProfile> {
    return this.api.patch(`/admin/customers/${id}/kyc`, { kycStatus });
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
    return this.api.get('/admin/transfers', {
      page,
      size,
      status: filters?.status,
      transferId: filters?.transferId,
      q: filters?.q,
      from: filters?.from,
      to: filters?.to,
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

  adminReconRuns(page = 0, size = 20): Observable<PageResponse<ReconRun>> {
    return this.api.get('/admin/recon/runs', { page, size });
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
    },
  ): Observable<PageResponse<AuditLog>> {
    return this.api.get('/admin/audit-logs', {
      page,
      size,
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
    return this.api.get('/admin/outbox', {
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
    return this.api.get('/admin/rbac/users', {
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
    return this.api.get('/bills/history', { page, size });
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
