import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TransferDetail } from '../../../core/models/domain.model';
import { ToastService } from '../../../core/services/toast.service';
import {
  buildTransferReceiptText,
  canRetryTransfer,
  copyText,
  transferRetryQueryParams,
} from '../../../core/utils/transfer-receipt.util';
import {
  parseTransferError,
  transferErrorI18nKey,
} from '../../../core/utils/transfer-error.util';
import { FriendlyTransferErrorPipe } from '../../pipes/friendly-transfer-error.pipe';
import { TransferStatusPipe } from '../../pipes/transfer-status.pipe';
import { MoneyVndPipe } from '../../pipes/money-vnd.pipe';

import { Store } from '@ngrx/store';
import { selectIsAdmin } from '../../../store/auth/auth.selectors';

export interface TransferDetailDialogData {
  detail: TransferDetail;
  isAdmin?: boolean;
}

@Component({
  selector: 'app-transfer-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
    MoneyVndPipe,
    FriendlyTransferErrorPipe,
    TransferStatusPipe,
  ],
  templateUrl: './transfer-detail-dialog.component.html',
  styleUrl: './transfer-detail-dialog.component.scss',
})
export class TransferDetailDialogComponent {
  private readonly data = inject<TransferDetailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TransferDetailDialogComponent>);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  readonly detail = this.data.detail;
  readonly isAdmin$ = this.store.select(selectIsAdmin);
  showTechDetails = false;

  get canRetry(): boolean {
    return canRetryTransfer(this.detail?.transfer?.status);
  }

  getFriendlyStepName(step: string): string {
    if (!step) {
      return '';
    }
    const clean = step.trim().toUpperCase();
    const key = `TRANSFER_DETAIL.STEP_${clean}`;
    const translated = this.i18n.instant(key);
    if (translated && translated !== key) {
      return translated;
    }
    switch (clean) {
      case 'DEBIT_SOURCE':
        return this.i18n.instant('TRANSFER_DETAIL.STEP_DEBIT');
      case 'CREDIT_DESTINATION':
        return this.i18n.instant('TRANSFER_DETAIL.STEP_CREDIT');
      case 'NAPAS_PAYMENT_RESPONSE':
      case 'NAPAS_PAYMENT':
      case 'NAPAS_TRANSFER':
      case 'INTERBANK_OUTWARD':
        return this.i18n.instant('TRANSFER_DETAIL.STEP_NAPAS');
      case 'COMPENSATE_SOURCE':
        return this.i18n.instant('TRANSFER_DETAIL.STEP_COMPENSATION');
      case 'FEE_REVENUE_GL':
      case 'FEE_DEBIT':
        return this.i18n.instant('TRANSFER_DETAIL.STEP_FEE_GL');
      default:
        return step;
    }
  }

  isInternalTechnicalDetail(detail: string | null | undefined): boolean {
    if (!detail) {
      return true;
    }
    const lower = detail.toLowerCase().trim();
    return lower.startsWith('ledger=') || lower.startsWith('od:') || lower.includes('uuid') || lower.startsWith('tx=');
  }

  async copyId(): Promise<void> {
    const id = this.detail?.transfer?.transactionId;
    if (!id) {
      return;
    }
    const ok = await copyText(id);
    this.toast[ok ? 'success' : 'error'](
      this.i18n.instant(ok ? 'TRANSFER_DETAIL.COPY_ID_OK' : 'TRANSFER_DETAIL.COPY_ID_FAIL'),
    );
  }

  async copyReceipt(): Promise<void> {
    const t = this.detail?.transfer;
    if (!t) {
      return;
    }
    const statusKey = `TRANSFER_STATUS.${t.status}`;
    const statusLabel = this.i18n.instant(statusKey);
    const reasonLabel = t.failureReason
      ? this.friendlyReason(t.failureReason)
      : undefined;
    const text = buildTransferReceiptText(t, this.i18n, {
      statusLabel: statusLabel !== statusKey ? statusLabel : t.status,
      reasonLabel,
    });
    const ok = await copyText(text);
    this.toast[ok ? 'success' : 'error'](
      this.i18n.instant(
        ok ? 'TRANSFER_DETAIL.COPY_RECEIPT_OK' : 'TRANSFER_DETAIL.COPY_RECEIPT_FAIL',
      ),
    );
  }

  retry(): void {
    const t = this.detail?.transfer;
    if (!t || !this.canRetry) {
      return;
    }
    this.dialogRef.close();
    void this.router.navigate(['/customer/payments/transfer'], {
      queryParams: transferRetryQueryParams(t),
    });
  }

  private friendlyReason(reason: string): string {
    const parsed = parseTransferError(reason);
    const key = transferErrorI18nKey(parsed.code);
    if (key) {
      const translated = this.i18n.instant(key);
      if (translated && translated !== key) {
        return translated;
      }
    }
    return parsed.detail || parsed.raw || reason;
  }
}
