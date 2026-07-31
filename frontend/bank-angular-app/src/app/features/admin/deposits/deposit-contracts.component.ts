import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { AdminTermDeposit, DepositProduct } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { exportToCsv } from '../../../core/utils/csv-export.util';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Per-contract drill-down: who holds which deposit, filters by status/tenor/owner/STK/maturity. */
@Component({
  selector: 'app-admin-deposit-contracts',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './deposit-contracts.component.html',
  styleUrl: './deposits.component.scss',
})
export class AdminDepositContractsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  products: DepositProduct[] = [];
  rows: AdminTermDeposit[] = [];
  listLoading = false;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  fStatus = '';
  fProduct = '';
  fSearch = '';
  fMaturityFrom = '';
  fMaturityTo = '';

  readonly statusOptions = ['', 'OPEN', 'MATURED', 'CLOSED_EARLY'];
  depositCols = ['owner', 'account', 'tenor', 'amount', 'rate', 'accrued', 'maturity', 'status'];

  ngOnInit(): void {
    this.api.adminAllDepositProducts().subscribe({
      next: (p) => (this.products = p),
      error: () => (this.products = []),
    });
    this.loadList();
  }

  get hasActiveFilters(): boolean {
    return !!(this.fStatus || this.fProduct || this.fSearch.trim() || this.fMaturityFrom || this.fMaturityTo);
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.loadList();
  }

  clearFilters(): void {
    this.fStatus = '';
    this.fProduct = '';
    this.fSearch = '';
    this.fMaturityFrom = '';
    this.fMaturityTo = '';
    this.pageIndex = 0;
    this.loadList();
  }

  exportCsv(): void {
    if (!this.rows.length) return;
    exportToCsv(
      `deposit_contracts_${new Date().toISOString().slice(0, 10)}`,
      [
        { key: 'ownerName', label: 'Owner' },
        { key: 'sourceAccountNumber', label: 'Source Account' },
        { key: 'productCode', label: 'Product Code' },
        { key: 'tenorMonths', label: 'Tenor (Months)' },
        { key: 'amount', label: 'Principal Amount' },
        { key: 'rateBps', label: 'Rate (bps)' },
        { key: 'accruedInterest', label: 'Accrued Interest' },
        { key: 'openedAt', label: 'Opened At' },
        { key: 'maturityDate', label: 'Maturity Date' },
        { key: 'status', label: 'Status' },
      ],
      this.rows as unknown as Record<string, unknown>[],
    );
    this.toast.success(this.i18n.instant('COMMON.EXPORT_SUCCESS'));
  }

  loadList(): void {
    this.listLoading = true;
    const search = this.fSearch.trim();
    const isUuid = UUID_RE.test(search);
    this.api
      .adminDeposits(this.pageIndex, this.pageSize, {
        status: this.fStatus || undefined,
        productCode: this.fProduct || undefined,
        // One search box, two human keys: UUID → owner id, digits → account number (STK)
        userId: isUuid ? search : undefined,
        accountNumber: search && !isUuid ? search : undefined,
        maturityFrom: this.fMaturityFrom || undefined,
        maturityTo: this.fMaturityTo || undefined,
      })
      .subscribe({
        next: (p) => {
          this.rows = p.items || [];
          this.totalElements = p.totalElements ?? this.rows.length;
          this.listLoading = false;
        },
        error: (err) => {
          this.rows = [];
          this.totalElements = 0;
          this.listLoading = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.loadList();
  }

  ratePct(bps: number): string {
    return (bps / 100).toFixed(2).replace(/\.?0+$/, '');
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? `${id.slice(0, 8)}…` : id;
  }
}
