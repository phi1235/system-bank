import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { FormControlErrorComponent } from '../../../shared/components/form-control-error/form-control-error.component';
import { ToastService } from '../../../core/services/toast.service';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BillCategoryItem, BillInquiryResult, BillProviderItem } from '../../../core/models/domain.model';
import {
  SoftOtpDialogComponent,
  SoftOtpDialogData,
} from '../../../shared/components/soft-otp-dialog/soft-otp-dialog.component';

export interface DisplayCategory {
  id: string;
  name: string;
  icon: string;
  themeClass: string;
  sampleCode: string;
}

@Component({
  selector: 'app-bills',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    TranslateModule,
    PageHeaderComponent,
    FormControlErrorComponent,
  ],
  templateUrl: './bills.component.html',
  styleUrl: './bills.component.scss',
})
export class BillsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);
  private readonly i18n = inject(TranslateService);
  private readonly api = inject(BankApiService);

  billForm!: FormGroup;
  inquiryResult: BillInquiryResult | null = null;
  loadingInquiry = false;
  submittingPayment = false;

  /** Categories loaded from API */
  categories: DisplayCategory[] = [];

  /** All providers grouped by category (loaded from API) */
  allProviders: BillProviderItem[] = [];

  /** Providers for the currently selected category */
  selectedCategoryProviders: BillProviderItem[] = [];

  ngOnInit(): void {
    this.billForm = this.fb.group({
      categoryId: ['', Validators.required],
      providerId: ['', Validators.required],
      customerCode: ['', [Validators.required, Validators.minLength(4)]],
    });

    this.loadCategories();
    this.loadAllProviders();
  }

  /** Load categories from Backend API (All data comes directly from Database) */
  private loadCategories(): void {
    this.api.billCategories().subscribe({
      next: (items: BillCategoryItem[]) => {
        this.categories = items.map((c) => ({
          id: c.id,
          name: c.name,
          icon: c.icon || 'receipt',
          themeClass: c.themeClass || 'cat-default',
          sampleCode: c.sampleCode || '',
        }));
        if (this.categories.length > 0) {
          this.billForm.patchValue({ categoryId: this.categories[0].id });
          this.onCategoryChange(this.categories[0].id);
        }
      },
      error: () => this.toast.error(this.i18n.instant('COMMON.LOAD_ERROR')),
    });
  }

  get currentSampleCode(): string {
    const catId = this.billForm?.value?.categoryId;
    const cat = this.categories.find((c) => c.id === catId);
    return cat ? cat.sampleCode : '';
  }

  fillSampleCode(): void {
    if (this.currentSampleCode) {
      this.billForm.patchValue({ customerCode: this.currentSampleCode });
    }
  }

  /** Load all providers from Backend API */
  private loadAllProviders(): void {
    this.api.billProviders().subscribe({
      next: (providers: BillProviderItem[]) => {
        this.allProviders = providers;
        // Refresh filtered list if category is already selected
        const catId = this.billForm.value.categoryId;
        if (catId) {
          this.selectedCategoryProviders = this.allProviders.filter((p) => p.categoryId === catId);
        }
      },
      error: () => this.toast.error(this.i18n.instant('COMMON.LOAD_ERROR')),
    });
  }

  get selectedProvider(): BillProviderItem | undefined {
    const providerId = this.billForm?.value?.providerId;
    return this.allProviders.find((p) => p.id === providerId);
  }

  onCategoryChange(catId: string): void {
    this.selectedCategoryProviders = this.allProviders.filter((p) => p.categoryId === catId);
    this.billForm.patchValue({ providerId: '', customerCode: '' });
    this.inquiryResult = null;
  }

  /** Inquiry bill from Backend API */
  inquireBill(): void {
    if (this.billForm.invalid) {
      this.billForm.markAllAsTouched();
      return;
    }

    const v = this.billForm.value;
    this.loadingInquiry = true;
    this.api.billInquiry(v.providerId, v.customerCode).subscribe({
      next: (result: BillInquiryResult) => {
        this.loadingInquiry = false;
        this.inquiryResult = result;
        this.toast.info(this.i18n.instant('BILLS.INQUIRY_SUCCESS'));
      },
      error: () => {
        this.loadingInquiry = false;
        this.toast.error(this.i18n.instant('BILLS.INQUIRY_NOT_FOUND'));
      },
    });
  }

  /** Pay bill via Backend API with OTP confirmation */
  payBill(): void {
    if (!this.inquiryResult) return;

    const otpData: SoftOtpDialogData = {
      title: this.i18n.instant('COMMON.SMART_OTP_TITLE'),
      amount: this.inquiryResult.amount,
      recipientName: this.inquiryResult.customerName,
      recipientAccount: this.billForm.value.customerCode,
    };

    this.dialog
      .open(SoftOtpDialogComponent, { data: otpData, width: '440px', disableClose: true })
      .afterClosed()
      .pipe(filter((res) => !!res && !!res.otp))
      .subscribe(() => {
        this.submittingPayment = true;
        const v = this.billForm.value;
        this.api.billPay(v.providerId, v.customerCode, this.inquiryResult!.amount).subscribe({
          next: () => {
            this.submittingPayment = false;
            this.toast.success(this.i18n.instant('BILLS.PAY_SUCCESS'));
            this.inquiryResult = null;
            this.billForm.patchValue({ providerId: '', customerCode: '' });
          },
          error: () => {
            this.submittingPayment = false;
            this.toast.error(this.i18n.instant('BILLS.PAY_ERROR'));
          },
        });
      });
  }
}
