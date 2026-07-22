import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { catchError, map, of } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { TransferDetailDialogComponent } from '../../../shared/components/transfer-detail-dialog/transfer-detail-dialog.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';
import { AccountMaskPipe } from '../../../shared/pipes/account-mask.pipe';
import { Account, Beneficiary, Transfer } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import {
  canRetryTransfer,
  copyText,
  transferRetryQueryParams,
} from '../../../core/utils/transfer-receipt.util';
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import {
  selectAccounts,
  selectAccountsLoading,
  selectTotalBalance,
} from '../../../store/accounts/accounts.selectors';
import { selectHasPermission, selectUsername } from '../../../store/auth/auth.selectors';
import { TransfersActions } from '../../../store/transfers/transfers.actions';
import {
  selectTransferHistory,
  selectTransferLoading,
  selectTransferPageMeta,
} from '../../../store/transfers/transfers.selectors';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatDialogModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    EnumLabelPipe,
    TransferStatusPipe,
    AccountMaskPipe,
    TranslateModule,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  username$ = this.store.select(selectUsername);
  accounts$ = this.store.select(selectAccounts);
  total$ = this.store.select(selectTotalBalance);
  accountsLoading$ = this.store.select(selectAccountsLoading);
  recent$ = this.store.select(selectTransferHistory);
  historyLoading$ = this.store.select(selectTransferLoading);
  historyMeta$ = this.store.select(selectTransferPageMeta);

  canTransfer$ = this.store.select(selectHasPermission(PERMISSIONS.IB_TRANSFER_VIEW));
  canOpenAccount$ = this.store.select(selectHasPermission(PERMISSIONS.IB_ACCOUNTS_OPEN));
  canNotif$ = this.store.select(selectHasPermission(PERMISSIONS.IB_NOTIFICATIONS_VIEW));
  canHistory$ = this.store.select(selectHasPermission(PERMISSIONS.IB_HISTORY_VIEW));

  unreadCount: number | null = null;
  beneficiaryCount: number | null = null;
  openingId: string | null = null;
  refreshing = false;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.refreshing = true;
    this.store.dispatch(AccountsActions.load());
    this.store.dispatch(TransfersActions.loadHistory({ page: 0, size: 5 }));
    this.loadUnread();
    this.loadBeneficiaries();
    // Clear spinner after a short tick — store loading flags drive real UI
    setTimeout(() => {
      this.refreshing = false;
    }, 400);
  }

  activeAccounts(list: Account[] | null | undefined): Account[] {
    return (list || []).filter((a) => a.status === 'ACTIVE');
  }

  frozenAccounts(list: Account[] | null | undefined): Account[] {
    return (list || []).filter((a) => a.status === 'FROZEN');
  }

  previewAccounts(list: Account[] | null | undefined): Account[] {
    return (list || []).slice(0, 4);
  }

  hasActiveSource(list: Account[] | null | undefined): boolean {
    return this.activeAccounts(list).length > 0;
  }

  primaryActiveId(list: Account[] | null | undefined): string | null {
    return this.activeAccounts(list)[0]?.id ?? null;
  }

  canRetry(t: Transfer): boolean {
    return canRetryTransfer(t?.status);
  }

  transferQuery(list: Account[] | null | undefined): Record<string, string> {
    const id = this.primaryActiveId(list);
    return id ? { from: id } : {};
  }

  async copyAccountNumber(a: Account, event?: Event): Promise<void> {
    event?.stopPropagation();
    event?.preventDefault();
    if (!a?.accountNumber) {
      return;
    }
    const ok = await copyText(a.accountNumber);
    this.toast[ok ? 'success' : 'error'](
      this.i18n.instant(ok ? 'CUSTOMER.ACCOUNTS_COPY_OK' : 'CUSTOMER.ACCOUNTS_COPY_FAIL'),
    );
  }

  async copyTransferId(t: Transfer, event?: Event): Promise<void> {
    event?.stopPropagation();
    event?.preventDefault();
    if (!t?.transactionId) {
      return;
    }
    const ok = await copyText(t.transactionId);
    this.toast[ok ? 'success' : 'error'](
      this.i18n.instant(ok ? 'TRANSFER_DETAIL.COPY_ID_OK' : 'TRANSFER_DETAIL.COPY_ID_FAIL'),
    );
  }

  openDetail(t: Transfer, event?: Event): void {
    event?.stopPropagation();
    event?.preventDefault();
    if (!t?.transactionId || this.openingId) {
      return;
    }
    this.openingId = t.transactionId;
    this.api.getTransferDetail(t.transactionId).subscribe({
      next: (detail) => {
        this.openingId = null;
        this.dialog.open(TransferDetailDialogComponent, {
          data: { detail },
          width: '560px',
          maxWidth: '95vw',
        });
      },
      error: () => {
        this.openingId = null;
        this.toast.error(this.i18n.instant('TRANSFER_DETAIL.LOAD_FAIL'));
      },
    });
  }

  retry(t: Transfer, event?: Event): void {
    event?.stopPropagation();
    event?.preventDefault();
    if (!this.canRetry(t)) {
      return;
    }
    void this.router.navigate(['/customer/payments/transfer'], {
      queryParams: transferRetryQueryParams(t),
    });
  }

  private loadUnread(): void {
    this.api
      .notificationUnreadCount()
      .pipe(catchError(() => of(null)))
      .subscribe((r) => {
        this.unreadCount = r ? (r.unread ?? 0) : null;
      });
  }

  private loadBeneficiaries(): void {
    this.api
      .listBeneficiaries()
      .pipe(
        map((list: Beneficiary[]) => list?.length ?? 0),
        catchError(() => of(null as number | null)),
      )
      .subscribe((n) => {
        this.beneficiaryCount = n;
      });
  }
}
