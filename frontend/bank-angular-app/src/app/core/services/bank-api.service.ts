import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/api.model';
import {
  Account,
  AuditLog,
  Beneficiary,
  CustomerProfile,
  LedgerEntry,
  NotificationItem,
  OutboxCounts,
  OutboxEvent,
  Transfer,
  TransferDetail,
  TransferRequest,
} from '../models/domain.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class BankApiService {
  private readonly api = inject(ApiService);

  // Notifications (customer inbox)
  myNotifications(page = 0, size = 20): Observable<PageResponse<NotificationItem>> {
    return this.api.get('/notifications', { page, size });
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

  /** Staff list/search: q = account number | account UUID | owner user UUID */
  adminListAccounts(
    page = 0,
    size = 20,
    q?: string,
    status?: string,
  ): Observable<PageResponse<Account>> {
    return this.api.get('/admin/accounts', { page, size, q, status });
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

  // Admin
  listCustomers(page = 0, size = 20, q?: string): Observable<PageResponse<CustomerProfile>> {
    return this.api.get('/admin/customers', { page, size, q });
  }

  updateKyc(id: string, kycStatus: string): Observable<CustomerProfile> {
    return this.api.patch(`/admin/customers/${id}/kyc`, { kycStatus });
  }

  adminTransfers(page = 0, size = 20, status?: string): Observable<PageResponse<Transfer>> {
    return this.api.get('/admin/transfers', { page, size, status });
  }

  auditLogs(page = 0, size = 20): Observable<PageResponse<AuditLog>> {
    return this.api.get('/admin/audit-logs', { page, size });
  }

  /** Staff outbox inspect/replay (default status DEAD on API). */
  adminOutboxList(
    page = 0,
    size = 20,
    status?: string,
  ): Observable<PageResponse<OutboxEvent>> {
    return this.api.get('/admin/outbox', { page, size, status });
  }

  adminOutboxCounts(): Observable<OutboxCounts> {
    return this.api.get('/admin/outbox/counts');
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

  rbacUsers(page = 0, size = 20, q?: string): Observable<PageResponse<RbacStaffUser>> {
    return this.api.get('/admin/rbac/users', { page, size, q });
  }

  assignRoles(userId: string, roles: string[]): Observable<RbacStaffUser> {
    return this.api.put(`/admin/rbac/users/${userId}/roles`, { roles });
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
}
