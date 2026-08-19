import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription, interval } from 'rxjs';
import { Account, SepayTopUpOrder } from '../../../../core/models/domain.model';
import { BankApiService } from '../../../../core/services/bank-api.service';
import { ToastService } from '../../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../../core/utils/http-error.util';
import { MoneyVndPipe } from '../../../../shared/pipes/money-vnd.pipe';

export interface TopUpDialogData {
  account: Account;
}

@Component({
  selector: 'app-topup-modal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
    MoneyVndPipe,
  ],
  templateUrl: './topup-modal.component.html',
  styleUrl: './topup-modal.component.scss',
})
export class TopupModalComponent implements OnInit, OnDestroy {
  readonly data = inject<TopUpDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TopupModalComponent, any>);
  private readonly fb = inject(FormBuilder);
  private readonly bankApi = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  mode: 'SEPAY' | 'SANDBOX' = 'SEPAY';
  step: 'FORM' | 'QR' | 'SUCCESS' = 'FORM';
  loading = false;
  order: SepayTopUpOrder | null = null;
  copiedKey: string | null = null;

  // Sandbox variables
  sandboxEnabled = true;
  sandboxLoading = false;
  sandboxRemainingQuota = 50000000;
  sandboxSelectedAmount = 5000000;
  readonly sandboxPresetAmounts = [1000000, 5000000, 10000000, 20000000];
  isSandboxSuccess = false;
  sandboxBalanceAfter = 0;
  sandboxAmountCredited = 0;

  // 15-minute countdown in seconds
  remainingSeconds = 900;
  private timerSub?: Subscription;
  private pollSub?: Subscription;

  readonly presetAmounts = [50000, 100000, 200000, 500000, 1000000, 2000000];

  readonly form = this.fb.nonNullable.group({
    amount: [100000, [Validators.required, Validators.min(1000), Validators.max(500000000)]],
    note: [''],
  });

  ngOnInit(): void {
    this.bankApi.getSandboxConfig().subscribe({
      next: (cfg) => {
        this.sandboxEnabled = cfg.enabled;
        this.sandboxRemainingQuota = cfg.maxDailyQuota;
      },
      error: () => {
        // Fallback default
        this.sandboxEnabled = true;
      },
    });
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  setMode(m: 'SEPAY' | 'SANDBOX'): void {
    this.mode = m;
  }

  selectPreset(amount: number): void {
    this.form.controls.amount.setValue(amount);
  }

  selectSandboxPreset(amount: number): void {
    this.sandboxSelectedAmount = amount;
  }

  executeSandboxTopup(): void {
    if (this.sandboxLoading) return;
    this.sandboxLoading = true;
    this.bankApi.sandboxTopup({
      accountId: this.data.account.id,
      amount: this.sandboxSelectedAmount,
    }).subscribe({
      next: (res) => {
        this.sandboxLoading = false;
        this.isSandboxSuccess = true;
        this.sandboxBalanceAfter = res.balanceAfter;
        this.sandboxAmountCredited = res.amount;
        this.sandboxRemainingQuota = res.remainingQuotaToday;
        this.data.account.balance = res.balanceAfter;
        this.step = 'SUCCESS';
        this.toast.success(this.i18n.instant('CUSTOMER.TOPUP_SUCCESS_TOAST'));
      },
      error: (err) => {
        this.sandboxLoading = false;
        const msg = resolveHttpErrorMessage(err, this.i18n.instant('CUSTOMER.TOPUP_CREATE_FAILED'));
        this.toast.error(msg);
      },
    });
  }

  createOrder(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.bankApi
      .createSepayTopUp({
        accountNumber: this.data.account.accountNumber,
        amount: this.form.controls.amount.value,
        note: this.form.controls.note.value,
      })
      .subscribe({
        next: (order) => {
          this.loading = false;
          this.order = order;
          this.isSandboxSuccess = false;
          this.step = 'QR';
          this.startPolling();
        },
        error: (err) => {
          this.loading = false;
          const msg = resolveHttpErrorMessage(err, this.i18n.instant('CUSTOMER.TOPUP_CREATE_FAILED'));
          this.toast.error(msg);
        },
      });
  }

  copyText(text: string, key: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.copiedKey = key;
      this.toast.success(this.i18n.instant('COMMON.COPIED'));
      setTimeout(() => {
        if (this.copiedKey === key) {
          this.copiedKey = null;
        }
      }, 2000);
    });
  }

  get formattedTime(): string {
    const mins = Math.floor(this.remainingSeconds / 60);
    const secs = this.remainingSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  private startPolling(): void {
    this.remainingSeconds = 900;
    this.timerSub = interval(1000).subscribe(() => {
      if (this.remainingSeconds > 0) {
        this.remainingSeconds--;
      } else {
        this.stopPolling();
      }
    });

    this.pollSub = interval(2000).subscribe(() => {
      if (!this.order) return;
      this.bankApi.getSepayOrder(this.order.orderCode).subscribe({
        next: (updatedOrder) => {
          if (updatedOrder.status === 'SUCCESS') {
            this.order = updatedOrder;
            this.isSandboxSuccess = false;
            this.step = 'SUCCESS';
            this.stopPolling();
            this.toast.success(this.i18n.instant('CUSTOMER.TOPUP_SUCCESS_TOAST'));
          }
        },
      });
    });
  }

  private stopPolling(): void {
    this.timerSub?.unsubscribe();
    this.pollSub?.unsubscribe();
  }

  close(): void {
    this.dialogRef.close(this.step === 'SUCCESS' ? true : null);
  }
}
