import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { filter } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { TopupModalComponent } from './topup-modal/topup-modal.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { ToastService } from '../../../core/services/toast.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { Account } from '../../../core/models/domain.model';
import { copyText } from '../../../core/utils/transfer-receipt.util';
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import { selectAccounts, selectAccountsLoading } from '../../../store/accounts/accounts.selectors';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    EnumLabelPipe,
    TranslateModule,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AccountsComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  accounts$ = this.store.select(selectAccounts);
  loading$ = this.store.select(selectAccountsLoading);
  canOpen$ = this.store.select(selectHasPermission(PERMISSIONS.IB_ACCOUNTS_OPEN));
  cols = ['accountNumber', 'accountType', 'balance', 'status', 'actions'];

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.store.dispatch(AccountsActions.load());
  }

  isActive(a: Account): boolean {
    return a?.status === 'ACTIVE';
  }

  isFrozen(a: Account): boolean {
    return a?.status === 'FROZEN';
  }

  open(type: 'PAYMENT' | 'SAVINGS' = 'PAYMENT'): void {
    const isPayment = type === 'PAYMENT';
    const data: ConfirmDialogData = {
      title: this.i18n.instant(
        isPayment ? 'CUSTOMER.OPEN_PAYMENT_CONFIRM_TITLE' : 'CUSTOMER.OPEN_SAVINGS_CONFIRM_TITLE',
      ),
      message: this.i18n.instant(
        isPayment ? 'CUSTOMER.OPEN_PAYMENT_CONFIRM_MSG' : 'CUSTOMER.OPEN_SAVINGS_CONFIRM_MSG',
      ),
      confirmLabel: this.i18n.instant(
        isPayment ? 'CUSTOMER.OPEN_PAYMENT' : 'CUSTOMER.OPEN_SAVINGS',
      ),
      destructive: false,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '440px' })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.store.dispatch(AccountsActions.open({ accountType: type }));
      });
  }

  openTopUp(a: Account): void {
    this.dialog
      .open(TopupModalComponent, { data: { account: a }, width: '500px', disableClose: true })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.refresh();
        }
      });
  }

  async copyAccountNumber(a: Account, event?: Event): Promise<void> {
    event?.stopPropagation();
    event?.preventDefault();
    if (!a?.accountNumber) {
      return;
    }
    const ok = await copyText(a.accountNumber);
    if (ok) {
      this.toast.success(this.i18n.instant('CUSTOMER.ACCOUNTS_COPY_OK'));
    } else {
      this.toast.error(this.i18n.instant('CUSTOMER.ACCOUNTS_COPY_FAIL'));
    }
  }
}
