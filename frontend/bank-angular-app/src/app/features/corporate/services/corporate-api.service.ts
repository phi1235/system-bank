import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/models/api.model';
import {
  ApprovalInstanceDetail,
  ApprovalPolicy,
  ApprovalTask,
  BatchProgress,
  CorporateAccount,
  CorporateMember,
  Corporation,
  PayoutBatch,
  PayoutItem,
  ReceiptArtifact,
  SimulatedPlan,
} from '../corporate.models';

@Injectable({
  providedIn: 'root',
})
export class CorporateApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  // ── Corporation Management ──
  getMyCorporations(): Observable<Corporation[]> {
    return this.http
      .get<ApiResponse<Corporation[]>>(`${this.baseUrl}/corporations`)
      .pipe(map((res) => res.data!));
  }

  getCorporation(corporateId: string): Observable<Corporation> {
    return this.http
      .get<ApiResponse<Corporation>>(`${this.baseUrl}/corporations/${corporateId}`)
      .pipe(map((res) => res.data!));
  }

  createCorporation(body: {
    taxId: string;
    companyName: string;
    shortName?: string;
    contactEmail?: string;
    contactPhone?: string;
    address?: string;
  }): Observable<Corporation> {
    return this.http
      .post<ApiResponse<Corporation>>(`${this.baseUrl}/corporations`, body)
      .pipe(map((res) => res.data!));
  }

  getMembers(corporateId: string): Observable<CorporateMember[]> {
    return this.http
      .get<ApiResponse<CorporateMember[]>>(`${this.baseUrl}/corporations/${corporateId}/members`)
      .pipe(map((res) => res.data!));
  }

  addMember(
    corporateId: string,
    body: { userId: string; roles: string[]; expiresAt?: string }
  ): Observable<CorporateMember> {
    return this.http
      .post<ApiResponse<CorporateMember>>(`${this.baseUrl}/corporations/${corporateId}/members`, body)
      .pipe(map((res) => res.data!));
  }

  updateMemberRoles(
    corporateId: string,
    targetUserId: string,
    roles: string[]
  ): Observable<CorporateMember> {
    return this.http
      .put<ApiResponse<CorporateMember>>(
        `${this.baseUrl}/corporations/${corporateId}/members/${targetUserId}/roles`,
        { roles }
      )
      .pipe(map((res) => res.data!));
  }

  getAccounts(corporateId: string): Observable<CorporateAccount[]> {
    return this.http
      .get<ApiResponse<CorporateAccount[]>>(`${this.baseUrl}/corporations/${corporateId}/accounts`)
      .pipe(map((res) => res.data!));
  }

  linkAccount(
    corporateId: string,
    body: {
      accountId: string;
      accountNumber: string;
      accountName?: string;
      currency?: string;
      isPrimary?: boolean;
      dailyPayoutLimit?: number;
    }
  ): Observable<CorporateAccount> {
    return this.http
      .post<ApiResponse<CorporateAccount>>(`${this.baseUrl}/corporations/${corporateId}/accounts/link`, body)
      .pipe(map((res) => res.data!));
  }

  createAndLinkAccount(
    corporateId: string,
    accountType = 'PAYMENT',
    currency = 'VND'
  ): Observable<CorporateAccount> {
    return this.http
      .post<ApiResponse<CorporateAccount>>(
        `${this.baseUrl}/corporations/${corporateId}/accounts/create`,
        { commandId: crypto.randomUUID(), accountType, currency }
      )
      .pipe(map((res) => res.data!));
  }

  // ── Approval Policies & Matrix ──
  getPolicies(corporateId: string): Observable<ApprovalPolicy[]> {
    return this.http
      .get<ApiResponse<ApprovalPolicy[]>>(
        `${this.baseUrl}/corporations/${corporateId}/approval-policies`
      )
      .pipe(map((res) => res.data!));
  }

  getActivePolicy(corporateId: string): Observable<ApprovalPolicy> {
    return this.http
      .get<ApiResponse<ApprovalPolicy>>(
        `${this.baseUrl}/corporations/${corporateId}/approval-policies/active`
      )
      .pipe(map((res) => res.data!));
  }

  createPolicy(corporateId: string, body: Partial<ApprovalPolicy>): Observable<ApprovalPolicy> {
    return this.http
      .post<ApiResponse<ApprovalPolicy>>(
        `${this.baseUrl}/corporations/${corporateId}/approval-policies`,
        body
      )
      .pipe(map((res) => res.data!));
  }

  activatePolicy(corporateId: string, policyId: string): Observable<ApprovalPolicy> {
    return this.http
      .put<ApiResponse<ApprovalPolicy>>(
        `${this.baseUrl}/corporations/${corporateId}/approval-policies/${policyId}/activate`,
        {}
      )
      .pipe(map((res) => res.data!));
  }

  simulatePlan(
    corporateId: string,
    totalAmount: number,
    currency = 'VND'
  ): Observable<SimulatedPlan> {
    return this.http
      .post<ApiResponse<SimulatedPlan>>(
        `${this.baseUrl}/corporations/${corporateId}/approval-policies/simulate`,
        { corporateId, totalAmount, currency }
      )
      .pipe(map((res) => res.data!));
  }

  // ── Payout Batches ──
  getBatches(
    corporateId: string,
    page = 0,
    size = 20
  ): Observable<{ content: PayoutBatch[]; totalElements: number }> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<{ content: PayoutBatch[]; totalElements: number }>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches`,
        { params }
      )
      .pipe(map((res) => res.data!));
  }

  getBatch(corporateId: string, batchId: string): Observable<PayoutBatch> {
    return this.http
      .get<ApiResponse<PayoutBatch>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}`
      )
      .pipe(map((res) => res.data!));
  }

  createBatch(
    corporateId: string,
    body: { sourceAccountId: string; sourceAccountNumber: string; batchName: string; currency?: string }
  ): Observable<PayoutBatch> {
    return this.http
      .post<ApiResponse<PayoutBatch>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches`,
        body
      )
      .pipe(map((res) => res.data!));
  }

  uploadExcel(corporateId: string, batchId: string, file: File): Observable<PayoutBatch> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http
      .post<ApiResponse<PayoutBatch>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/upload`,
        formData
      )
      .pipe(map((res) => res.data!));
  }

  downloadTemplate(corporateId: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/corporations/${corporateId}/payout-batches/template`,
      { responseType: 'blob' }
    );
  }

  downloadBatchErrorReport(corporateId: string, batchId: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/error-report`,
      { responseType: 'blob' }
    );
  }

  submitBatch(corporateId: string, batchId: string): Observable<PayoutBatch> {
    return this.http
      .post<ApiResponse<PayoutBatch>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/submit`,
        {}
      )
      .pipe(map((res) => res.data!));
  }

  cancelBatch(corporateId: string, batchId: string, reason?: string): Observable<PayoutBatch> {
    return this.http
      .post<ApiResponse<PayoutBatch>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/cancel`,
        { reason: reason || 'User requested cancellation' }
      )
      .pipe(map((res) => res.data!));
  }

  retryBatch(corporateId: string, batchId: string): Observable<PayoutBatch> {
    return this.http
      .post<ApiResponse<PayoutBatch>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/retry`,
        {}
      )
      .pipe(map((res) => res.data!));
  }

  getBatchItems(
    corporateId: string,
    batchId: string,
    page = 0,
    size = 50
  ): Observable<{ content: PayoutItem[]; totalElements: number }> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<{ content: PayoutItem[]; totalElements: number }>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/items`,
        { params }
      )
      .pipe(map((res) => res.data!));
  }

  getBatchProgress(corporateId: string, batchId: string): Observable<BatchProgress> {
    return this.http
      .get<ApiResponse<BatchProgress>>(
        `${this.baseUrl}/corporations/${corporateId}/payout-batches/${batchId}/progress`
      )
      .pipe(map((res) => res.data!));
  }

  // ── Approval Tasks & Inbox ──
  getInbox(): Observable<ApprovalTask[]> {
    return this.http
      .get<ApiResponse<ApprovalTask[]>>(`${this.baseUrl}/approval-tasks/inbox`)
      .pipe(map((res) => res.data!));
  }

  getInstanceDetail(batchId: string): Observable<ApprovalInstanceDetail> {
    return this.http
      .get<ApiResponse<ApprovalInstanceDetail>>(
        `${this.baseUrl}/approval-tasks/batches/${batchId}/instance`
      )
      .pipe(map((res) => res.data!));
  }

  createChallenge(taskId: string): Observable<{
    challengeId: string;
    nonce: string;
    challengeType: string;
    payloadHash: string;
    expiresAt: string;
  }> {
    return this.http
      .post<
        ApiResponse<{
          challengeId: string;
          nonce: string;
          challengeType: string;
          payloadHash: string;
          expiresAt: string;
        }>
      >(`${this.baseUrl}/approval-tasks/${taskId}/challenge`, {})
      .pipe(map((res) => res.data!));
  }

  approveTask(
    taskId: string,
    body: {
      comments?: string;
      challengeNonce?: string;
      authCode?: string;
      signatureReference?: string;
    }
  ): Observable<ApprovalTask> {
    return this.http
      .post<ApiResponse<ApprovalTask>>(`${this.baseUrl}/approval-tasks/${taskId}/approve`, body)
      .pipe(map((res) => res.data!));
  }

  rejectTask(taskId: string, reason: string): Observable<ApprovalTask> {
    return this.http
      .post<ApiResponse<ApprovalTask>>(`${this.baseUrl}/approval-tasks/${taskId}/reject`, {
        reason,
      })
      .pipe(map((res) => res.data!));
  }

  returnTask(taskId: string, reason: string): Observable<ApprovalTask> {
    return this.http
      .post<ApiResponse<ApprovalTask>>(`${this.baseUrl}/approval-tasks/${taskId}/return`, {
        reason,
      })
      .pipe(map((res) => res.data!));
  }

  // ── Receipts ──
  getBatchReceipts(corporateId: string, batchId: string): Observable<ReceiptArtifact[]> {
    return this.http
      .get<ApiResponse<ReceiptArtifact[]>>(
        `${this.baseUrl}/corporations/${corporateId}/receipts/batches/${batchId}`
      )
      .pipe(map((res) => res.data!));
  }

  downloadReceipt(corporateId: string, artifactId: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/corporations/${corporateId}/receipts/${artifactId}/download`,
      { responseType: 'blob' }
    );
  }
}
