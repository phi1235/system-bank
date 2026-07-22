import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
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
    MatDialogModule,
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

  rows: Account[] = [];
  selected: Account | null = null;
  loading = false;
  total = 0;
  cols = ['accountNumber', 'userId', 'balance', 'status', 'actions'];
  canFreeze$ = this.store.select(selectHasPermission(PERMISSIONS.ACCOUNTS_FREEZE_EXECUTE));

  form = this.fb.nonNullable.group({
    q: [''],
    status: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const { q, status } = this.form.getRawValue();
    this.api.adminListAccounts(0, 50, q.trim() || undefined, status || undefined).subscribe({
      next: (page) => {
        this.rows = page.items || [];
        this.total = page.totalElements ?? this.rows.length;
        this.loading = false;
        if (this.selected) {
          this.selected = this.rows.find((r) => r.id === this.selected?.id) ?? this.selected;
        }
      },
      error: () => {
        this.rows = [];
        this.total = 0;
        this.loading = false;
      },
    });
  }

  clearFilters(): void {
    this.form.reset({ q: '', status: '' });
    this.load();
  }

  select(account: Account): void {
    this.selected = account;
  }

  freeze(account: Account = this.selected!): void {
    if (!account) return;
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
        switchMap(() => this.api.freezeAccount(account.id)),
      )
      .subscribe({
        next: (a) => {
          this.patchRow(a);
          this.selected = a;
          this.toast.success(this.i18n.instant('ADMIN.FROZEN_OK'));
        },
        error: (err) => {
          this.toast.error(err?.error?.error?.message || this.i18n.instant('ADMIN.FREEZE_FAIL'));
        },
      });
  }

  unfreeze(account: Account = this.selected!): void {
    if (!account) return;
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
        switchMap(() => this.api.unfreezeAccount(account.id)),
      )
      .subscribe({
        next: (a) => {
          this.patchRow(a);
          this.selected = a;
          this.toast.success(this.i18n.instant('ADMIN.ACTIVE_OK'));
        },
        error: (err) => {
          this.toast.error(err?.error?.error?.message || this.i18n.instant('ADMIN.UNFREEZE_FAIL'));
        },
      });
  }

  private patchRow(updated: Account): void {
    this.rows = this.rows.map((x) => (x.id === updated.id ? updated : x));
  }
}
