import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter, forkJoin } from 'rxjs';
import {
  Account,
  AutoSweepOperation,
  AutoSweepProfile,
  DepositProduct,
  DepositQuote,
  SweepProduct,
  TermDeposit,
} from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  SoftOtpDialogComponent,
  SoftOtpDialogData,
} from '../../../shared/components/soft-otp-dialog/soft-otp-dialog.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';

@Component({
  selector: 'app-wealth',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './wealth.component.html',
  styleUrl: './wealth.component.scss',
})
export class WealthComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  loading = false;
  products: DepositProduct[] = [];
  accounts: Account[] = [];
  deposits: TermDeposit[] = [];
  autoSweeps: AutoSweepProfile[] = [];
  sweepProducts: SweepProduct[] = [];
  sweepOperations: AutoSweepOperation[] = [];
  sweepAccountId = '';
  sweepProductCode = '';
  sweepThreshold: number | null = null;
  sweepSaving = false;
  sweepToggling = false;

  sourceAccountId = '';
  productCode = '';
  amount: number | null = null;
  quote: DepositQuote | null = null;
  quoteLoading = false;
  submitting = false;
  closingId: string | null = null;

  depositCols = ['product', 'amount', 'rate', 'maturityDate', 'interest', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    forkJoin({
      products: this.api.depositProducts(),
      accounts: this.api.listAccounts(),
      deposits: this.api.myDeposits(),
    }).subscribe({
      next: ({ products, accounts, deposits }) => {
        this.products = products;
        this.accounts = accounts.filter((a) => a.status === 'ACTIVE');
        this.deposits = deposits;
        if (!this.sourceAccountId && this.accounts.length) {
          this.sourceAccountId = this.accounts[0].id;
        }
        if (!this.productCode && products.length) {
          this.productCode = products[0].code;
        }
        this.loading = false;
        this.loadAutoSweep();
      },
      error: (err) => {
        this.loading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  get selectedProduct(): DepositProduct | undefined {
    return this.products.find((p) => p.code === this.productCode);
  }

  private loadAutoSweep(): void {
    forkJoin({
      autoSweeps: this.api.myAutoSweeps(),
      sweepProducts: this.api.autoSweepProducts(),
    }).subscribe({
      next: ({ autoSweeps, sweepProducts }) => {
        this.autoSweeps = autoSweeps;
        this.sweepProducts = sweepProducts;
        if (!this.sweepAccountId || !this.paymentAccounts.some((a) => a.id === this.sweepAccountId)) {
          this.sweepAccountId = autoSweeps[0]?.sourceAccountId
            || this.paymentAccounts[0]?.id
            || '';
        }
        this.selectSweepAccount();
      },
      error: (err) => {
        this.autoSweeps = [];
        this.sweepProducts = [];
        this.sweepOperations = [];
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  get paymentAccounts(): Account[] {
    return this.accounts.filter((a) => a.accountType === 'PAYMENT');
  }

  get selectedSweepProfile(): AutoSweepProfile | undefined {
    return this.autoSweeps.find((p) => p.sourceAccountId === this.sweepAccountId);
  }

  get selectedSweepProduct(): SweepProduct | undefined {
    return this.sweepProducts.find((product) => product.code === this.sweepProductCode);
  }

  get canSaveAutoSweep(): boolean {
    const product = this.selectedSweepProduct;
    return !!(
      this.sweepAccountId
      && product
      && this.sweepThreshold !== null
      && this.sweepThreshold >= product.minThreshold
    );
  }

  selectSweepAccount(): void {
    const profile = this.selectedSweepProfile;
    if (profile) {
      this.sweepProductCode = profile.productCode;
      this.sweepThreshold = profile.thresholdAmount;
      this.api.autoSweepOperations(profile.sourceAccountId).subscribe({
        next: (items) => {
          if (this.sweepAccountId === profile.sourceAccountId) this.sweepOperations = items;
        },
        error: (err) => {
          this.sweepOperations = [];
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
    } else {
      this.sweepOperations = [];
      if (!this.sweepProductCode || !this.selectedSweepProduct) {
        this.sweepProductCode = this.sweepProducts[0]?.code || '';
      }
      this.sweepThreshold = this.selectedSweepProduct?.defaultThreshold ?? null;
    }
  }

  saveAutoSweep(): void {
    if (!this.canSaveAutoSweep || this.sweepThreshold === null) {
      return;
    }
    this.sweepSaving = true;
    this.api.saveAutoSweep(
      this.sweepAccountId,
      this.sweepProductCode,
      this.sweepThreshold,
      this.selectedSweepProfile?.version,
    ).subscribe({
      next: () => {
        this.sweepSaving = false;
        this.toast.success(this.i18n.instant('CUSTOMER.AUTO_SWEEP_SAVED'));
        this.load();
      },
      error: (err) => {
        this.sweepSaving = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  toggleAutoSweep(): void {
    const profile = this.selectedSweepProfile;
    if (!profile || this.sweepToggling) return;
    this.sweepToggling = true;
    this.api.setAutoSweepEnabled(profile.sourceAccountId, profile.status !== 'ENABLED').subscribe({
      next: () => {
        this.sweepToggling = false;
        this.load();
      },
      error: (err) => {
        this.sweepToggling = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  get canQuote(): boolean {
    return !!(this.productCode && this.amount && this.amount > 0);
  }

  refreshQuote(): void {
    if (!this.canQuote) {
      this.quote = null;
      return;
    }
    this.quoteLoading = true;
    this.api.depositQuote(this.productCode, this.amount as number).subscribe({
      next: (q) => {
        this.quote = q;
        this.quoteLoading = false;
      },
      error: (err) => {
        this.quote = null;
        this.quoteLoading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  submitOpen(): void {
    if (!this.canQuote || !this.sourceAccountId || this.submitting) {
      return;
    }

    const otpData: SoftOtpDialogData = {
      title: this.i18n.instant('COMMON.SMART_OTP_TITLE'),
      amount: this.amount as number,
    };

    this.dialog
      .open(SoftOtpDialogComponent, { data: otpData, width: '440px', disableClose: true })
      .afterClosed()
      .pipe(filter((res) => !!res && !!res.otp))
      .subscribe(() => {
        this.submitting = true;
        this.api.openDeposit(this.sourceAccountId, this.productCode, this.amount as number).subscribe({
          next: () => {
            this.submitting = false;
            this.amount = null;
            this.quote = null;
            this.toast.success(this.i18n.instant('CUSTOMER.WEALTH_OPEN_DONE'));
            this.load();
          },
          error: (err) => {
            this.submitting = false;
            this.toast.error(resolveHttpErrorMessage(err, this.i18n));
          },
        });
      });
  }

  confirmClose(d: TermDeposit): void {
    if (this.closingId) {
      return;
    }
    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.WEALTH_CLOSE_TITLE'),
      message: this.i18n.instant('CUSTOMER.WEALTH_CLOSE_WARN', {
        rate: this.ratePct(d.earlyRateBps),
      }),
      confirmLabel: this.i18n.instant('CUSTOMER.WEALTH_CLOSE_CONFIRM'),
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .subscribe((ok) => {
        if (ok) {
          this.doClose(d);
        }
      });
  }

  ratePct(bps: number): string {
    return (bps / 100).toFixed(2).replace(/\.?0+$/, '');
  }

  accountLabel(a: Account): string {
    return `${a.accountNumber} · ${new MoneyVndPipe().transform(a.balance)}`;
  }

  private doClose(d: TermDeposit): void {
    this.closingId = d.id;
    this.api.closeDeposit(d.id).subscribe({
      next: (closed) => {
        this.closingId = null;
        this.toast.success(
          this.i18n.instant('CUSTOMER.WEALTH_CLOSE_DONE', {
            interest: new MoneyVndPipe().transform(closed.interest),
          }),
        );
        this.load();
      },
      error: (err) => {
        this.closingId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }
}
