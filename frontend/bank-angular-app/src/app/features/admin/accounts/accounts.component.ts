import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { copyText } from '../../../core/utils/transfer-receipt.util';
import { Account } from '../../../core/models/domain.model';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { selectHasPermission } from '../../../store/auth/auth.selectors';
import { filter, switchMap } from 'rxjs';

@Component({
  selector: 'app-admin-accounts',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    MatTableModule,
    MatPaginatorModule,
    MatDialogModule,
    MatTooltipModule,
    PageHeaderComponent,
    MoneyVndPipe,
    EnumLabelPipe,
    TranslateModule,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AdminAccountsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);

  rows: Account[] = [];
  selected: Account | null = null;
  loading = false;
  busyId: string | null = null;
  total = 0;
  pageIndex = 0;
  pageSize = 20;
  get totalElements(): number {
    return this.total;
  }
  cols = ['accountNumber', 'accountType', 'userId', 'balance', 'status', 'actions'];
  canFreeze$ = this.store.select(selectHasPermission(PERMISSIONS.ACCOUNTS_FREEZE_EXECUTE));

  form = this.fb.nonNullable.group({
    q: [''],
    status: [''],
    accountType: [''],
  });

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    const status = (qp.get('status') || '').toUpperCase();
    const accountType = (qp.get('accountType') || '').toUpperCase();
    const q = qp.get('q') || '';
    const patch: { q?: string; status?: string; accountType?: string } = {};
    if (q) {
      patch.q = q;
    }
    if (status === 'ACTIVE' || status === 'FROZEN' || status === 'CLOSED') {
      patch.status = status;
    }
    if (accountType === 'PAYMENT' || accountType === 'SAVINGS') {
      patch.accountType = accountType;
    }
    if (Object.keys(patch).length) {
      this.form.patchValue(patch);
    }
    this.load();
  }

  get hasActiveFilters(): boolean {
    const v = this.form.getRawValue();
    return !!(v.q.trim() || v.status || v.accountType);
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.loading = true;
    const { q, status, accountType } = this.form.getRawValue();
    this.api
      .adminListAccounts(
        this.pageIndex,
        this.pageSize,
        q.trim() || undefined,
        status || undefined,
        accountType || undefined,
      )
      .subscribe({
        next: (page) => {
          this.rows = page.items || [];
          this.total = page.totalElements ?? this.rows.length;
          this.loading = false;
          if (this.selected) {
            this.selected =
              this.rows.find((r) => r.id === this.selected?.id) ?? this.selected;
          }
        },
        error: (err) => {
          this.rows = [];
          this.total = 0;
          this.loading = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  clearFilters(): void {
    this.form.reset({ q: '', status: '', accountType: '' });
    this.pageIndex = 0;
    this.load();
  }

  select(account: Account): void {
    this.selected = account;
  }

  openDetail(account: Account, event?: Event): void {
    event?.stopPropagation();
    this.selected = account;
    this.api.adminAccountDetail(account.id).subscribe({
      next: (a) => {
        this.selected = a;
        this.patchRow(a);
      },
      error: (err) => {
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  async copyTextValue(value: string | null | undefined, okKey: string, failKey: string): Promise<void> {
    if (!value) {
      return;
    }
    const ok = await copyText(value);
    this.toast[ok ? 'success' : 'error'](this.i18n.instant(ok ? okKey : failKey));
  }

  freeze(account: Account = this.selected!): void {
    if (!account || this.busyId) {
      return;
    }
    const data: ConfirmDialogData = {
      title: this.i18n.instant('ADMIN.FREEZE_TITLE'),
      message: this.i18n.instant('ADMIN.FREEZE_CONFIRM', {
        account: account.accountNumber,
      }),
      confirmLabel: this.i18n.instant('ADMIN.FREEZE'),
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busyId = account.id;
          return this.api.freezeAccount(account.id);
        }),
      )
      .subscribe({
        next: (a) => {
          this.busyId = null;
          this.patchRow(a);
          this.selected = a;
          this.toast.success(this.i18n.instant('ADMIN.FROZEN_OK'));
        },
        error: (err) => {
          this.busyId = null;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  unfreeze(account: Account = this.selected!): void {
    if (!account || this.busyId) {
      return;
    }
    const data: ConfirmDialogData = {
      title: this.i18n.instant('ADMIN.UNFREEZE_TITLE'),
      message: this.i18n.instant('ADMIN.UNFREEZE_CONFIRM', {
        account: account.accountNumber,
      }),
      confirmLabel: this.i18n.instant('ADMIN.UNFREEZE'),
      destructive: false,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busyId = account.id;
          return this.api.unfreezeAccount(account.id);
        }),
      )
      .subscribe({
        next: (a) => {
          this.busyId = null;
          this.patchRow(a);
          this.selected = a;
          this.toast.success(this.i18n.instant('ADMIN.ACTIVE_OK'));
        },
        error: (err) => {
          this.busyId = null;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  private patchRow(updated: Account): void {
    this.rows = this.rows.map((x) => (x.id === updated.id ? updated : x));
  }
}
