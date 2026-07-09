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
}
