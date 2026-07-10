import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../models/api.model';
import {
  Account,
  AuditLog,
  CustomerProfile,
  Transfer,
  TransferRequest,
} from '../models/domain.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class BankApiService {
  private readonly api = inject(ApiService);

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

  updateProfile(body: { fullName?: string; phone?: string; address?: string }): Observable<CustomerProfile> {
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

  freezeAccount(id: string): Observable<Account> {
    return this.api.post(`/admin/accounts/${id}/freeze`, {});
  }

  unfreezeAccount(id: string): Observable<Account> {
    return this.api.post(`/admin/accounts/${id}/unfreeze`, {});
  }

  // Transfers
  transfer(body: TransferRequest, idempotencyKey: string): Observable<Transfer> {
    return this.api.post('/transactions/transfers', body, {
      'Idempotency-Key': idempotencyKey,
    });
  }

  myTransfers(page = 0, size = 20): Observable<PageResponse<Transfer>> {
    return this.api.get('/transactions/transfers', { page, size });
  }

  getTransfer(id: string): Observable<Transfer> {
    return this.api.get(`/transactions/transfers/${id}`);
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
}
