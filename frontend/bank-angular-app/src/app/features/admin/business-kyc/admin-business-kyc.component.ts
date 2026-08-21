import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BankApiService } from '../../../core/services/bank-api.service';
import { AdminBusinessKycItem, AdminKycReviewRequest } from '../../../core/models/domain.model';

@Component({
  selector: 'app-admin-business-kyc',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './admin-business-kyc.component.html',
  styleUrl: './admin-business-kyc.component.scss',
})
export class AdminBusinessKycComponent implements OnInit {
  private readonly bankApi = inject(BankApiService);
  private readonly translate = inject(TranslateService);

  businesses: AdminBusinessKycItem[] = [];
  statusFilter = 'PENDING_KYC';
  loading = false;
  submitting = false;
  error: string | null = null;
  successMessage: string | null = null;

  // Review Modal State
  selectedBusiness: AdminBusinessKycItem | null = null;
  showReviewModal = false;
  rejectReason = '';

  ngOnInit(): void {
    this.loadBusinesses();
  }

  loadBusinesses(): void {
    this.loading = true;
    this.error = null;
    this.bankApi.listAdminBusinesses(this.statusFilter || undefined).subscribe({
      next: (data) => {
        this.businesses = data || [];
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || this.translate.instant('ADMIN.BUSINESS_KYC.LOAD_ERROR');
        this.loading = false;
      },
    });
  }

  onFilterChange(status: string): void {
    this.statusFilter = status;
    this.loadBusinesses();
  }

  openReviewModal(biz: AdminBusinessKycItem): void {
    this.selectedBusiness = biz;
    this.rejectReason = '';
    this.showReviewModal = true;
  }

  closeReviewModal(): void {
    this.showReviewModal = false;
    this.selectedBusiness = null;
  }

  submitReview(action: 'APPROVE' | 'REJECT'): void {
    if (!this.selectedBusiness) return;
    if (action === 'REJECT' && !this.rejectReason.trim()) {
      alert(this.translate.instant('ADMIN.BUSINESS_KYC.REJECT_REASON_REQUIRED'));
      return;
    }

    this.submitting = true;
    this.error = null;
    this.successMessage = null;

    const payload: AdminKycReviewRequest = {
      action,
      rejectReason: action === 'REJECT' ? this.rejectReason.trim() : undefined,
    };

    this.bankApi.reviewBusinessKyc(this.selectedBusiness.id, payload).subscribe({
      next: () => {
        this.submitting = false;
        this.closeReviewModal();
        this.successMessage = action === 'APPROVE'
          ? this.translate.instant('ADMIN.BUSINESS_KYC.APPROVE_SUCCESS')
          : this.translate.instant('ADMIN.BUSINESS_KYC.REJECT_SUCCESS');
        this.loadBusinesses();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message || this.translate.instant('ADMIN.BUSINESS_KYC.REVIEW_ERROR');
      },
    });
  }
}
