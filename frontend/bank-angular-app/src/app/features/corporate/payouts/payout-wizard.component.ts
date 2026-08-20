import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../../core/services/toast.service';
import { CorporateAccount, PayoutBatch, PayoutItem, SimulatedPlan } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-payout-wizard',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatStepperModule,
    MatProgressBarModule,
    TranslateModule,
  ],
  templateUrl: './payout-wizard.component.html',
  styleUrl: './payout-wizard.component.scss',
})
export class PayoutWizardComponent implements OnInit {
  private readonly api = inject(CorporateApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  corporateId = '';
  accounts: CorporateAccount[] = [];

  batchName = '';
  selectedAccountId = '';
  selectedFile: File | null = null;

  createdBatch: PayoutBatch | null = null;
  previewItems: PayoutItem[] = [];
  simulatedPlan: SimulatedPlan | null = null;

  creating = false;
  uploading = false;
  submitting = false;

  get canSubmit(): boolean {
    return (
      !!this.createdBatch &&
      this.createdBatch.validItems > 0 &&
      this.createdBatch.invalidItems === 0 &&
      this.createdBatch.status === 'READY_FOR_SUBMISSION'
    );
  }

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (this.corporateId) {
      this.loadAccounts();
    }
  }

  loadAccounts() {
    this.api.getAccounts(this.corporateId).subscribe({
      next: (list) => {
        this.accounts = list;
        if (list.length > 0) {
          const primary = list.find((a) => a.isPrimary) || list[0];
          this.selectedAccountId = primary.accountId;
        }
      },
    });
  }

  createBatch(stepper: any) {
    const acc = this.accounts.find((a) => a.accountId === this.selectedAccountId);
    if (!acc) return;

    this.creating = true;
    this.api
      .createBatch(this.corporateId, {
        sourceAccountId: acc.accountId,
        sourceAccountNumber: acc.accountNumber,
        batchName: this.batchName,
        currency: acc.currency,
      })
      .subscribe({
        next: (batch) => {
          this.createdBatch = batch;
          this.creating = false;
          stepper.next();
        },
        error: (err) => {
          this.toast.error(err.message || this.translate.instant('CORPORATE.BATCH_CREATE_ERROR'));
          this.creating = false;
        },
      });
  }

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (file) this.selectedFile = file;
  }

  onFileDropped(event: DragEvent) {
    event.preventDefault();
    if (event.dataTransfer?.files.length) {
      this.selectedFile = event.dataTransfer.files[0];
    }
  }

  downloadTemplate() {
    this.api.downloadTemplate(this.corporateId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'mau_chi_tra_luong.xlsx';
        a.click();
        window.URL.revokeObjectURL(url);
      },
    });
  }

  downloadErrorReport() {
    if (!this.createdBatch) return;
    this.api.downloadBatchErrorReport(this.corporateId, this.createdBatch.id).subscribe({
      next: (blob) => this.saveBlob(blob, `danh_sach_dong_loi_${this.createdBatch!.id}.xlsx`),
      error: () => this.toast.error(this.translate.instant('CORPORATE.ERROR_REPORT_DOWNLOAD_ERROR')),
    });
  }

  private saveBlob(blob: Blob, filename: string) {
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }

  uploadExcel(stepper: any) {
    if (!this.createdBatch || !this.selectedFile) return;

    this.uploading = true;
    this.api.uploadExcel(this.corporateId, this.createdBatch.id, this.selectedFile).subscribe({
      next: (batch) => {
        this.createdBatch = batch;
        this.uploading = false;
        this.loadPreviewItems();
        this.simulatePlanForBatch();
        stepper.next();
      },
      error: (err) => {
        this.toast.error(err.message || this.translate.instant('CORPORATE.EXCEL_UPLOAD_ERROR'));
        this.uploading = false;
      },
    });
  }

  loadPreviewItems() {
    if (!this.createdBatch) return;
    this.api.getBatchItems(this.corporateId, this.createdBatch.id, 0, 10).subscribe({
      next: (res) => (this.previewItems = res.content || []),
    });
  }

  simulatePlanForBatch() {
    if (!this.createdBatch || this.createdBatch.totalAmount <= 0) return;
    this.api
      .simulatePlan(this.corporateId, this.createdBatch.totalAmount, this.createdBatch.currency)
      .subscribe({
        next: (plan) => (this.simulatedPlan = plan),
      });
  }

  submitBatch() {
    if (!this.createdBatch) return;

    this.submitting = true;
    this.api.submitBatch(this.corporateId, this.createdBatch.id).subscribe({
      next: (res) => {
        this.toast.success(this.translate.instant('CORPORATE.BATCH_SUBMIT_SUCCESS'));
        this.router.navigate(['/corporate/payouts', res.id]);
      },
      error: (err) => {
        this.toast.error(err.message || this.translate.instant('CORPORATE.BATCH_SUBMIT_ERROR'));
        this.submitting = false;
      },
    });
  }
}
