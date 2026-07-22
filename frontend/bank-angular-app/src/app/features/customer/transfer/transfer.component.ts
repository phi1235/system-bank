import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { Subscription, debounceTime, distinctUntilChanged, filter, take } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { TransferDetailDialogComponent } from '../../../shared/components/transfer-detail-dialog/transfer-detail-dialog.component';
import { FriendlyTransferErrorPipe } from '../../../shared/pipes/friendly-transfer-error.pipe';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { AccountInquiryResponse, BankItem, Beneficiary, Transfer, TransferQuote } from '../../../core/models/domain.model';
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
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import { selectAccounts } from '../../../store/accounts/accounts.selectors';
import { TransfersActions } from '../../../store/transfers/transfers.actions';
import {
  selectLastTransfer,
  selectTransferCreating,
  selectTransferError,
} from '../../../store/transfers/transfers.selectors';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-transfer',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatTooltipModule,
    MatTabsModule,
    PageHeaderComponent,
    MoneyVndPipe,
    FriendlyTransferErrorPipe,
    TransferStatusPipe,
    EnumLabelPipe,
    TranslateModule,
  ],
  templateUrl: './transfer.component.html',
  styleUrl: './transfer.component.scss',
})
export class TransferComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly i18n = inject(TranslateService);
  private readonly api = inject(BankApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private amountSub?: Subscription;
  private toAccSub?: Subscription;
  private quoteReq = 0;

  accounts$ = this.store.select(selectAccounts);
  creating$ = this.store.select(selectTransferCreating);
  last$ = this.store.select(selectLastTransfer);
  error$ = this.store.select(selectTransferError);
  canExecute$ = this.store.select(selectHasPermission(PERMISSIONS.IB_TRANSFER_EXECUTE));

  banks: BankItem[] = [];
  selectedBankCode = 'SYSTEM_BANK';
  activeTab: 'INTERNAL' | 'INTERBANK' = 'INTERNAL';

  inquiryLoading = false;
  inquiryResult: AccountInquiryResponse | null = null;
  inquiryError: string | null = null;

  beneficiaries: Beneficiary[] = [];
  selectedBeneficiaryId = '';
  quote: TransferQuote | null = null;
  quoteLoading = false;
  quoteError: string | null = null;
  noActiveSource = false;
  retryPrefill = false;
  private detailOpening = false;
  private accountsSub?: Subscription;

  form = this.fb.nonNullable.group({
    fromAccountId: ['', Validators.required],
    toAccountNumber: ['', [Validators.required, Validators.pattern(/^\d{6,19}$/)]],
    amount: [0, [Validators.required, Validators.min(1)]],
    description: [''],
  });

  ngOnInit(): void {
    this.form.patchValue({ description: this.i18n.instant('CUSTOMER.DEFAULT_DESC') });
    this.store.dispatch(AccountsActions.load());
    this.store.dispatch(TransfersActions.clearStatus());
    this.loadBanks();
    this.loadBeneficiaries();
    this.applyQueryPrefill();
    this.refreshQuote();

    this.accountsSub = this.accounts$.subscribe((accounts) => {
      const list = accounts || [];
      const active = list.filter((a) => a.status === 'ACTIVE');
      this.noActiveSource = active.length === 0;
      if (active.length > 0 && !this.form.controls.fromAccountId.value) {
        this.form.patchValue({ fromAccountId: active[0].id });
      }
    });

    this.amountSub = this.form.controls.amount.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.refreshQuote());

    this.toAccSub = this.form.controls.toAccountNumber.valueChanges
      .pipe(debounceTime(400), distinctUntilChanged())
      .subscribe(() => this.performInquiry());
  }

  ngOnDestroy(): void {
    this.amountSub?.unsubscribe();
    this.toAccSub?.unsubscribe();
    this.accountsSub?.unsubscribe();
  }

  loadBanks(): void {
    this.api.listBanks().subscribe({
      next: (res) => {
        this.banks = res || [];
      },
      error: () => {
        this.banks = [
          { bankCode: 'SYSTEM_BANK', shortName: 'SystemBank', fullName: 'Ngân hàng Nội bộ SystemBank', bin: '970499', logoUrl: '', napasSupported: true, isInternal: true },
          { bankCode: '970415', shortName: 'VietinBank', fullName: 'Ngân hàng TMCP Công thương VN', bin: '970415', logoUrl: '', napasSupported: true, isInternal: false }
        ];
      }
    });
  }

  switchTab(tab: 'INTERNAL' | 'INTERBANK'): void {
    this.activeTab = tab;
    if (tab === 'INTERNAL') {
      this.selectedBankCode = 'SYSTEM_BANK';
    } else {
      this.selectedBankCode = '';
    }
    this.inquiryResult = null;
    this.inquiryError = null;
    if (this.selectedBankCode) {
      this.performInquiry();
    }
  }

  onBankChange(bankCode: string): void {
    this.selectedBankCode = bankCode;
    this.inquiryResult = null;
    this.inquiryError = null;
    if (this.selectedBankCode) {
      this.performInquiry();
    }
  }

  performInquiry(): void {
    if (this.activeTab === 'INTERBANK' && !this.selectedBankCode) {
      this.inquiryResult = null;
      this.inquiryError = null;
      return;
    }
    const accNum = this.form.controls.toAccountNumber.value?.trim();
    if (!accNum || accNum.length < 6) {
      this.inquiryResult = null;
      this.inquiryError = null;
      return;
    }
    this.inquiryLoading = true;
    this.inquiryError = null;
    this.api.accountInquiry({ bankCode: this.selectedBankCode, accountNumber: accNum }).subscribe({
      next: (res) => {
        this.inquiryLoading = false;
        this.inquiryResult = res;
      },
      error: (err) => {
        this.inquiryLoading = false;
        this.inquiryResult = null;
        this.inquiryError = err?.error?.message || this.i18n.instant('ERRORS.ACCOUNT_NOT_FOUND');
      }
    });
  }

  loadBeneficiaries(): void {
    this.api.listBeneficiaries().subscribe({
      next: (items) => {
        this.beneficiaries = items || [];
        const current = this.form.controls.toAccountNumber.value;
        const match = this.beneficiaries.find((b) => b.accountNumber === current);
        this.selectedBeneficiaryId = match?.id ?? '';
      },
      error: () => {
        this.beneficiaries = [];
      },
    });
  }

  onBeneficiaryPicked(id: string): void {
    this.selectedBeneficiaryId = id || '';
    if (!id) {
      return;
    }
    const found = this.beneficiaries.find((b) => b.id === id);
    if (found) {
      this.form.patchValue({ toAccountNumber: found.accountNumber });
      this.performInquiry();
    }
  }

  onToAccountTyped(): void {
    const current = this.form.controls.toAccountNumber.value;
    const match = this.beneficiaries.find((b) => b.accountNumber === current);
    this.selectedBeneficiaryId = match?.id ?? '';
  }

  canRetry(status: string | null | undefined): boolean {
    return canRetryTransfer(status);
  }

  async copyLastId(t: Transfer): Promise<void> {
    if (!t?.transactionId) {
      return;
    }
    const ok = await copyText(t.transactionId);
    this.toast[ok ? 'success' : 'error'](
      this.i18n.instant(ok ? 'TRANSFER_DETAIL.COPY_ID_OK' : 'TRANSFER_DETAIL.COPY_ID_FAIL'),
    );
  }

  async copyLastReceipt(t: Transfer): Promise<void> {
    if (!t) {
      return;
    }
    const statusKey = `TRANSFER_STATUS.${t.status}`;
    const statusLabel = this.i18n.instant(statusKey);
    const reasonLabel = t.failureReason ? this.friendlyReason(t.failureReason) : undefined;
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

  openLastDetail(t: Transfer): void {
    if (!t?.transactionId || this.detailOpening) {
      return;
    }
    this.detailOpening = true;
    this.api.getTransferDetail(t.transactionId).subscribe({
      next: (detail) => {
        this.detailOpening = false;
        this.dialog.open(TransferDetailDialogComponent, {
          data: { detail },
          width: '560px',
          maxWidth: '95vw',
        });
      },
      error: () => {
        this.detailOpening = false;
        this.toast.error(this.i18n.instant('TRANSFER_DETAIL.LOAD_FAIL'));
      },
    });
  }

  retryLast(t: Transfer): void {
    if (!t || !canRetryTransfer(t.status)) {
      return;
    }
    this.applyTransferPrefill(t);
    this.retryPrefill = true;
    this.toast.success(this.i18n.instant('CUSTOMER.RESULT_RETRY_HINT'));
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: transferRetryQueryParams(t),
      replaceUrl: true,
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const amount = Number(v.amount);
    const fee = this.quote?.feeAmount ?? 0;
    const total = this.quote?.totalDebit ?? amount;

    const bankObj = this.banks.find((b) => b.bankCode === this.selectedBankCode);
    const bankName = bankObj ? bankObj.shortName : 'SystemBank';
    const recipientName = this.inquiryResult ? this.inquiryResult.accountName : '';

    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.CONFIRM_TITLE'),
      message: this.i18n.instant('CUSTOMER.CONFIRM_MSG', {
        amount: amount.toLocaleString('vi-VN'),
        fee: Number(fee).toLocaleString('vi-VN'),
        total: Number(total).toLocaleString('vi-VN'),
        to: v.toAccountNumber + (recipientName ? ` (${recipientName} - ${bankName})` : ''),
        desc: v.description || '',
      }),
      confirmLabel: this.i18n.instant('CUSTOMER.CONFIRM_TRANSFER'),
      destructive: false,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '440px' })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => {
        const key = crypto.randomUUID();
        this.store.dispatch(
          TransfersActions.create({
            request: {
              fromAccountId: v.fromAccountId,
              toAccountNumber: v.toAccountNumber,
              amount,
              description: v.description || undefined,
              currency: 'VND',
              transferType: this.activeTab,
              targetBankCode: this.selectedBankCode,
              targetAccountName: recipientName || undefined,
            },
            idempotencyKey: key,
          }),
        );
      });
  }

  private refreshQuote(): void {
    const amount = Number(this.form.controls.amount.value || 0);
    const reqId = ++this.quoteReq;
    this.quoteLoading = true;
    this.quoteError = null;
    this.api.transferQuote(amount > 0 ? amount : undefined).subscribe({
      next: (q) => {
        if (reqId !== this.quoteReq) return;
        this.quote = q;
        this.quoteLoading = false;
      },
      error: () => {
        if (reqId !== this.quoteReq) return;
        this.quote = null;
        this.quoteLoading = false;
        this.quoteError = this.i18n.instant('CUSTOMER.QUOTE_LOAD_FAIL');
      },
    });
  }

  private applyQueryPrefill(): void {
    const q = this.route.snapshot.queryParamMap;
    const to = q.get('to');
    const from = q.get('from');
    const amountRaw = q.get('amount');
    const desc = q.get('desc');
    const isRetry = q.get('retry') === '1';

    const patch: Partial<{
      fromAccountId: string;
      toAccountNumber: string;
      amount: number;
      description: string;
    }> = {};

    if (to && /^\d{6,19}$/.test(to)) {
      patch.toAccountNumber = to;
    }
    if (from) {
      patch.fromAccountId = from;
    }
    if (amountRaw != null && amountRaw !== '') {
      const amount = Number(amountRaw);
      if (Number.isFinite(amount) && amount > 0) {
        patch.amount = amount;
      }
    }
    if (desc != null && desc !== '') {
      patch.description = desc;
    }

    if (Object.keys(patch).length) {
      this.form.patchValue(patch);
    }
    this.retryPrefill = isRetry && !!(patch.toAccountNumber || patch.amount);

    if (from) {
      this.accounts$.pipe(take(1)).subscribe((accounts) => {
        const exists = (accounts || []).some((a) => a.id === from);
        if (exists) {
          this.form.patchValue({ fromAccountId: from });
        }
      });
    }
  }

  private applyTransferPrefill(t: Transfer): void {
    this.form.patchValue({
      fromAccountId: t.fromAccountId || '',
      toAccountNumber: t.toAccountNumber || '',
      amount: Number(t.amount) || 0,
      description: t.description || this.i18n.instant('CUSTOMER.DEFAULT_DESC'),
    });
    this.onToAccountTyped();
    this.refreshQuote();
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
