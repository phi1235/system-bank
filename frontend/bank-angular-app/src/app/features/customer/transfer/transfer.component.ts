import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { Subscription, debounceTime, distinctUntilChanged, filter } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { FriendlyTransferErrorPipe } from '../../../shared/pipes/friendly-transfer-error.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { BankApiService } from '../../../core/services/bank-api.service';
import { Beneficiary, TransferQuote } from '../../../core/models/domain.model';
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
    PageHeaderComponent,
    MoneyVndPipe,
    FriendlyTransferErrorPipe,
    TransferStatusPipe,
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
  private readonly dialog = inject(MatDialog);
  private amountSub?: Subscription;
  private quoteReq = 0;

  accounts$ = this.store.select(selectAccounts);
  creating$ = this.store.select(selectTransferCreating);
  last$ = this.store.select(selectLastTransfer);
  error$ = this.store.select(selectTransferError);
  canExecute$ = this.store.select(selectHasPermission(PERMISSIONS.IB_TRANSFER_EXECUTE));

  beneficiaries: Beneficiary[] = [];
  selectedBeneficiaryId = '';
  quote: TransferQuote | null = null;
  quoteLoading = false;
  quoteError: string | null = null;
  noActiveSource = false;
  private accountsSub?: Subscription;

  form = this.fb.nonNullable.group({
    fromAccountId: ['', Validators.required],
    toAccountNumber: ['', [Validators.required, Validators.pattern(/^\d{8,14}$/)]],
    amount: [0, [Validators.required, Validators.min(1)]],
    description: [''],
  });

  ngOnInit(): void {
    this.form.patchValue({ description: this.i18n.instant('CUSTOMER.DEFAULT_DESC') });
    this.store.dispatch(AccountsActions.load());
    this.store.dispatch(TransfersActions.clearStatus());
    this.loadBeneficiaries();
    this.refreshQuote();

    const to = this.route.snapshot.queryParamMap.get('to');
    if (to && /^\d{8,14}$/.test(to)) {
      this.form.patchValue({ toAccountNumber: to });
    }

    this.accountsSub = this.accounts$.subscribe((accounts) => {
      const list = accounts || [];
      const active = list.filter((a) => a.status === 'ACTIVE');
      // Show CTA when user has no ACTIVE source (none yet, or all frozen).
      this.noActiveSource = active.length === 0;
      if (active.length === 1 && !this.form.controls.fromAccountId.value) {
        this.form.patchValue({ fromAccountId: active[0].id });
      }
    });

    this.amountSub = this.form.controls.amount.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.refreshQuote());
  }

  ngOnDestroy(): void {
    this.amountSub?.unsubscribe();
    this.accountsSub?.unsubscribe();
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
    }
  }

  onToAccountTyped(): void {
    const current = this.form.controls.toAccountNumber.value;
    const match = this.beneficiaries.find((b) => b.accountNumber === current);
    this.selectedBeneficiaryId = match?.id ?? '';
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
    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.CONFIRM_TITLE'),
      message: this.i18n.instant('CUSTOMER.CONFIRM_MSG', {
        amount: amount.toLocaleString('vi-VN'),
        fee: Number(fee).toLocaleString('vi-VN'),
        total: Number(total).toLocaleString('vi-VN'),
        to: v.toAccountNumber,
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
}
