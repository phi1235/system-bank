import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Account, TopUpResponse } from '../../../../core/models/domain.model';
import { BankApiService } from '../../../../core/services/bank-api.service';
import { ToastService } from '../../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../../core/utils/http-error.util';
import { MoneyVndPipe } from '../../../../shared/pipes/money-vnd.pipe';

export interface AdminTopUpDialogData {
  account: Account;
}

@Component({
  selector: 'app-admin-top-up-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
    MoneyVndPipe,
  ],
  templateUrl: './admin-top-up-dialog.component.html',
  styleUrl: './admin-top-up-dialog.component.scss',
})
export class AdminTopUpDialogComponent {
  readonly data = inject<AdminTopUpDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AdminTopUpDialogComponent, TopUpResponse | null>);
  private readonly fb = inject(FormBuilder);
  private readonly bankApi = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  loading = false;

  readonly form = this.fb.nonNullable.group({
    amount: [10000000, [Validators.required, Validators.min(0.01), Validators.max(50000000)]],
    description: ['', [Validators.maxLength(200)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const val = this.form.getRawValue();
    this.loading = true;
    this.bankApi.adminTopUp(this.data.account.id, { amount: val.amount, description: val.description }).subscribe({
      next: (res) => {
        this.toast.success(this.i18n.instant('ADMIN.TOPUP_SUCCESS'));
        this.dialogRef.close(res);
      },
      error: (err) => {
        this.loading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
