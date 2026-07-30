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
import {
  SoftOtpDialogComponent,
  SoftOtpDialogData,
} from '../../../shared/components/soft-otp-dialog/soft-otp-dialog.component';

export interface BillCategory {
  id: string;
  nameKey: string;
  icon: string;
}

export interface BillProvider {
  id: string;
  categoryId: string;
  name: string;
  code: string;
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

  billForm!: FormGroup;
  inquiryResult: { customerName: string; amount: number; period: string } | null = null;
  loadingInquiry = false;
  submittingPayment = false;

  categories: BillCategory[] = [
    { id: 'ELECTRICITY', nameKey: 'BILLS.CAT_ELECTRICITY', icon: 'bolt' },
    { id: 'WATER', nameKey: 'BILLS.CAT_WATER', icon: 'water_drop' },
    { id: 'INTERNET', nameKey: 'BILLS.CAT_INTERNET', icon: 'wifi' },
    { id: 'MOBILE_TOPUP', nameKey: 'BILLS.CAT_MOBILE', icon: 'phone_android' },
  ];

  providers: Record<string, BillProvider[]> = {
    ELECTRICITY: [
      { id: 'EVN_HANOI', categoryId: 'ELECTRICITY', name: 'EVN Hà Nội', code: 'EVNHN' },
      { id: 'EVN_HCM', categoryId: 'ELECTRICITY', name: 'EVN TP.Hồ Chí Minh', code: 'EVNHCM' },
    ],
    WATER: [
      { id: 'HAWACO', categoryId: 'WATER', name: 'Nước sạch Hà Nội', code: 'HAWACO' },
      { id: 'SAWACO', categoryId: 'WATER', name: 'Nước sạch Sài Gòn', code: 'SAWACO' },
    ],
    INTERNET: [
      { id: 'VIETTEL_NET', categoryId: 'INTERNET', name: 'Viettel Telecom', code: 'VTNET' },
      { id: 'FPT_TELECOM', categoryId: 'INTERNET', name: 'FPT Telecom', code: 'FPTNET' },
    ],
    MOBILE_TOPUP: [
      { id: 'VT_TOPUP', categoryId: 'MOBILE_TOPUP', name: 'Viettel Mobile', code: 'VTTOPUP' },
      { id: 'VINA_TOPUP', categoryId: 'MOBILE_TOPUP', name: 'VinaPhone', code: 'VINATOPUP' },
    ],
  };

  selectedCategoryProviders: BillProvider[] = [];

  ngOnInit(): void {
    this.billForm = this.fb.group({
      categoryId: ['ELECTRICITY', Validators.required],
      providerId: ['', Validators.required],
      customerCode: ['', [Validators.required, Validators.minLength(4)]],
    });

    this.onCategoryChange('ELECTRICITY');
  }

  onCategoryChange(catId: string): void {
    this.selectedCategoryProviders = this.providers[catId] || [];
    this.billForm.patchValue({ providerId: '', customerCode: '' });
    this.inquiryResult = null;
  }

  inquireBill(): void {
    if (this.billForm.invalid) {
      this.billForm.markAllAsTouched();
      return;
    }

    this.loadingInquiry = true;
    setTimeout(() => {
      this.loadingInquiry = false;
      const code = this.billForm.value.customerCode;
      this.inquiryResult = {
        customerName: 'NGUYEN VAN A',
        amount: 450000,
        period: 'Tháng 07/2026',
      };
      this.toast.info('Đã tra cứu xong thông tin hóa đơn.');
    }, 600);
  }

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
        setTimeout(() => {
          this.submittingPayment = false;
          this.toast.success('Thanh toán hóa đơn thành công!');
          this.inquiryResult = null;
          this.billForm.reset({ categoryId: 'ELECTRICITY' });
        }, 800);
      });
  }
}
