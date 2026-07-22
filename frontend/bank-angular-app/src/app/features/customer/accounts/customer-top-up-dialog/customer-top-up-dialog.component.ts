import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Account, TopUpResponse } from '../../../../core/models/domain.model';
import { BankApiService } from '../../../../core/services/bank-api.service';
import { ToastService } from '../../../../core/services/toast.service';
import { MoneyVndPipe } from '../../../../shared/pipes/money-vnd.pipe';
import { EnumLabelPipe } from '../../../../shared/pipes/enum-label.pipe';

export interface CustomerTopUpDialogData {
  accounts: Account[];
  selectedAccountId?: string;
}

@Component({
  selector: 'app-customer-top-up-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
    MoneyVndPipe,
    EnumLabelPipe,
  ],
  templateUrl: './customer-top-up-dialog.component.html',
  styleUrl: './customer-top-up-dialog.component.scss',
})
export class CustomerTopUpDialogComponent {
  readonly data = inject<CustomerTopUpDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<CustomerTopUpDialogComponent, TopUpResponse | null>);
  private readonly fb = inject(FormBuilder);
  private readonly bankApi = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  loading = false;

  readonly presets = [
    { label: '500.000đ', value: 500000 },
    { label: '1.000.000đ', value: 1000000 },
    { label: '5.000.000đ', value: 5000000 },
    { label: '10.000.000đ', value: 10000000 },
  ];

  readonly activeAccounts = (this.data.accounts || []).filter((a) => a.status === 'ACTIVE');

  readonly form = this.fb.nonNullable.group({
    accountId: [
      this.data.selectedAccountId || (this.activeAccounts[0]?.id ?? ''),
      [Validators.required],
    ],
    amount: [1000000, [Validators.required, Validators.min(10000), Validators.max(50000000)]],
    description: [''],
  });

  selectPreset(val: number): void {
    this.form.patchValue({ amount: val });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const val = this.form.getRawValue();
    this.loading = true;
    this.bankApi.customerTopUp(val.accountId, { amount: val.amount, description: val.description }).subscribe({
      next: (res) => {
        this.toast.success(this.i18n.instant('CUSTOMER.TOPUP_SUCCESS'));
        this.dialogRef.close(res);
      },
      error: (err) => {
        this.loading = false;
        this.toast.error(err?.error?.message || this.i18n.instant('CUSTOMER.TOPUP_FAIL'));
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
