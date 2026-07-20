import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
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
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './statement.component.html',
  styleUrl: './statement.component.scss',
})
export class StatementComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  accountId = '';
  account: Account | null = null;
  rows: LedgerEntry[] = [];
  loading = false;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  cols = ['createdAt', 'entryType', 'description', 'amount', 'referenceId'];

  filter = this.fb.nonNullable.group({
    entryType: [''],
    from: [''],
    to: [''],
  });

  ngOnInit(): void {
    this.accountId = this.route.snapshot.paramMap.get('id') || '';
    if (!this.accountId) {
      this.toast.error(this.i18n.instant('CUSTOMER.STATEMENT_NO_ACCOUNT'));
      return;
    }
    this.loadAccount();
    this.load();
  }

  loadAccount(): void {
    this.api.getAccount(this.accountId).subscribe({
      next: (a) => (this.account = a),
      error: () => {
        this.account = null;
      },
    });
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  page(ev: PageEvent): void {
    this.pageIndex = ev.pageIndex;
    this.pageSize = ev.pageSize;
    this.load();
  }

  load(): void {
    if (!this.accountId) return;
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
          this.toast.error(err?.message || this.i18n.instant('CUSTOMER.STATEMENT_LOAD_FAIL'));
        },
      });
  }

  /** Local date yyyy-mm-dd → start of day UTC ISO */
  private toInstantStart(date: string | undefined): string | undefined {
    if (!date) return undefined;
    return `${date}T00:00:00.000Z`;
  }

  private toInstantEnd(date: string | undefined): string | undefined {
    if (!date) return undefined;
    return `${date}T23:59:59.999Z`;
  }
}
