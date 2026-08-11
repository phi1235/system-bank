import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { CustomerProfile, KycCase, KycDocument } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';

export interface KycDetailDialogData {
  customer: CustomerProfile;
  canApprove: boolean;
}

@Component({
  selector: 'app-kyc-detail-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule,
    MatIconModule, MatInputModule, TranslateModule],
  templateUrl: './kyc-detail-dialog.component.html',
  styleUrl: './kyc-detail-dialog.component.scss',
})
export class KycDetailDialogComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly ref = inject(MatDialogRef<KycDetailDialogComponent>);
  readonly data: KycDetailDialogData = inject(MAT_DIALOG_DATA);

  kycCase: KycCase | null = null;
  loading = true;
  loadError = '';
  openingId: string | null = null;
  saving = false;
  updated = false;
  reason = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.getAdminKyc(this.data.customer.id).subscribe({
      next: (result) => { this.kycCase = result; this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.loadError = resolveHttpErrorMessage(err, this.i18n);
      },
    });
  }

  openDocument(document: KycDocument): void {
    if (this.openingId) return;
    this.openingId = document.id;
    this.api.downloadAdminKycDocument(document.id).subscribe({
      next: (blob) => {
        this.openingId = null;
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener,noreferrer');
        window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => {
        this.openingId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  checkerDecision(decision: 'APPROVE' | 'REJECT'): void {
    if (!this.kycCase || this.saving) return;
    this.saving = true;
    this.api.decideAdminKyc(this.kycCase.id, decision, this.reason.trim()).subscribe({
      next: (result) => {
        this.kycCase = result;
        this.saving = false;
        this.updated = true;
        this.toast.success(this.i18n.instant('ADMIN.KYC_DECISION_OK'));
      },
      error: (err) => {
        this.saving = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  close(): void { this.ref.close(this.updated); }
}
