import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { Account, LedgerEntry } from '../../../core/models/domain.model';

@Component({
  selector: 'app-statement',
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
    MatTableModule,
    MatPaginatorModule,
    MatTooltipModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    EnumLabelPipe,
    TranslateModule,
  ],
  templateUrl: './statement.component.html',
  styleUrl: './statement.component.scss',
})
export class StatementComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  accountId = '';
  account: Account | null = null;
  accounts: Account[] = [];
  rows: LedgerEntry[] = [];
  loading = false;
  exporting = false;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  cols = ['createdAt', 'entryType', 'description', 'amount', 'referenceId'];
  activeQuick: '7d' | '30d' | '90d' | 'month' | null = null;

  filter = this.fb.nonNullable.group({
    entryType: [''],
    from: [''],
    to: [''],
  });

  ngOnInit(): void {
    this.loadAccountList();
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id') || '';
      if (id === this.accountId && this.account) {
        return;
      }
      this.accountId = id;
      this.pageIndex = 0;
      this.rows = [];
      this.totalElements = 0;
      if (!this.accountId) {
        this.account = null;
        return;
      }
      this.loadAccount();
      this.load();
    });
  }

  get hasActiveFilters(): boolean {
    const f = this.filter.getRawValue();
    return !!(f.entryType || f.from || f.to);
  }

  get missingAccount(): boolean {
    return !this.accountId;
  }

  loadAccountList(): void {
    this.api.listAccounts().subscribe({
      next: (list) => {
        this.accounts = list || [];
        // Deep-link missing /id → auto-pick single account
        if (!this.accountId && this.accounts.length === 1) {
          this.switchAccount(this.accounts[0].id);
        }
      },
      error: () => {
        this.accounts = [];
      },
    });
  }

  loadAccount(): void {
    if (!this.accountId) {
      this.account = null;
      return;
    }
    this.api.getAccount(this.accountId).subscribe({
      next: (a) => (this.account = a),
      error: () => {
        this.account = null;
      },
    });
  }

  switchAccount(id: string): void {
    if (!id || id === this.accountId) {
      return;
    }
    this.activeQuick = null;
    // paramMap subscription reloads account + ledger for the new id
    void this.router.navigate(['/customer/accounts', id, 'statement']);
  }

  applyFilters(): void {
    if (!this.validateRange()) {
      return;
    }
    this.activeQuick = null;
    this.pageIndex = 0;
    this.load();
  }

  resetFilters(): void {
    this.filter.reset({ entryType: '', from: '', to: '' });
    this.activeQuick = null;
    this.pageIndex = 0;
    this.load();
  }

  applyQuickRange(kind: '7d' | '30d' | '90d' | 'month'): void {
    const today = this.localDate(new Date());
    let from: string;
    if (kind === 'month') {
      const now = new Date();
      from = this.localDate(new Date(now.getFullYear(), now.getMonth(), 1));
    } else {
      const days = kind === '7d' ? 7 : kind === '30d' ? 30 : 90;
      const d = new Date();
      d.setDate(d.getDate() - (days - 1));
      from = this.localDate(d);
    }
    this.filter.patchValue({ from, to: today });
    this.activeQuick = kind;
    this.pageIndex = 0;
    this.load();
  }

  page(ev: PageEvent): void {
    this.pageIndex = ev.pageIndex;
    this.pageSize = ev.pageSize;
    this.load();
  }

  async copyRef(ref: string | null | undefined): Promise<void> {
    if (!ref) {
      return;
    }
    try {
      await navigator.clipboard.writeText(ref);
      this.toast.success(this.i18n.instant('CUSTOMER.STATEMENT_COPY_OK'));
    } catch {
      this.toast.error(this.i18n.instant('CUSTOMER.STATEMENT_COPY_FAIL'));
    }
  }

  exportCsv(): void {
    if (!this.accountId || this.exporting) {
      return;
    }
    if (!this.validateRange()) {
      return;
    }
    this.exporting = true;
    const f = this.filter.getRawValue();
    this.api
      .exportAccountStatementCsv(this.accountId, {
        entryType: f.entryType || undefined,
        from: this.toInstantStart(f.from),
        to: this.toInstantEnd(f.to),
      })
      .subscribe({
        next: (blob) => {
          this.exporting = false;
          const name =
            (this.account?.accountNumber
              ? `statement-${this.account.accountNumber}`
              : `statement-${this.accountId}`) + '.csv';
          this.downloadBlob(blob, name);
          this.toast.success(this.i18n.instant('CUSTOMER.STATEMENT_EXPORT_OK'));
        },
        error: (err) => {
          this.exporting = false;
          this.toast.error(
            err instanceof HttpErrorResponse
              ? resolveHttpErrorMessage(err, this.i18n)
              : this.i18n.instant('CUSTOMER.STATEMENT_EXPORT_FAIL'),
          );
        },
      });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  load(): void {
    if (!this.accountId) {
      return;
    }
    if (!this.validateRange(false)) {
      return;
    }
    this.loading = true;
    const f = this.filter.getRawValue();
    this.api
      .accountStatement(this.accountId, {
        page: this.pageIndex,
        size: this.pageSize,
        entryType: f.entryType || undefined,
        from: this.toInstantStart(f.from),
        to: this.toInstantEnd(f.to),
      })
      .subscribe({
        next: (page) => {
          this.rows = page.items || [];
          this.totalElements = page.totalElements || 0;
          this.pageIndex = page.page ?? this.pageIndex;
          this.pageSize = page.size || this.pageSize;
          this.loading = false;
        },
        error: (err) => {
          this.rows = [];
          this.totalElements = 0;
          this.loading = false;
          this.toast.error(
            err instanceof HttpErrorResponse
              ? resolveHttpErrorMessage(err, this.i18n)
              : this.i18n.instant('CUSTOMER.STATEMENT_LOAD_FAIL'),
          );
        },
      });
  }

  /** Validate from <= to. toast=true shows user message. */
  private validateRange(toast = true): boolean {
    const f = this.filter.getRawValue();
    if (f.from && f.to && f.from > f.to) {
      if (toast) {
        this.toast.error(this.i18n.instant('CUSTOMER.STATEMENT_INVALID_RANGE'));
      }
      return false;
    }
    return true;
  }

  private localDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /** Local date yyyy-mm-dd → start of day UTC ISO */
  private toInstantStart(date: string | undefined): string | undefined {
    if (!date) {
      return undefined;
    }
    return `${date}T00:00:00.000Z`;
  }

  private toInstantEnd(date: string | undefined): string | undefined {
    if (!date) {
      return undefined;
    }
    return `${date}T23:59:59.999Z`;
  }
}
