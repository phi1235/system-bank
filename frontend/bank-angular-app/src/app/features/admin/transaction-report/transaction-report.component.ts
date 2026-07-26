import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TransactionReport } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';

interface ChartBar {
  day: string;
  heightPct: number;
  hasFailed: boolean;
  tooltip: string;
}

@Component({
  selector: 'app-admin-transaction-report',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    MoneyVndPipe,
    TransferStatusPipe,
    TranslateModule,
  ],
  templateUrl: './transaction-report.component.html',
  styleUrl: './transaction-report.component.scss',
})
export class AdminTransactionReportComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  from = '';
  to = '';
  accountId = '';
  loading = false;
  report: TransactionReport | null = null;
  bars: ChartBar[] = [];
  maxCompletedAmount = 0;

  statusCols = ['status', 'count', 'totalAmount'];
  topCols = ['fromAccountId', 'transferCount', 'totalAmount'];

  ngOnInit(): void {
    this.load();
  }

  get hasActiveFilters(): boolean {
    return !!(this.from || this.to || this.accountId.trim());
  }

  applyFilters(): void {
    this.load();
  }

  clearFilters(): void {
    this.from = '';
    this.to = '';
    this.accountId = '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api
      .adminTransactionReport({
        from: this.from || undefined,
        to: this.to || undefined,
        accountId: this.accountId.trim() || undefined,
      })
      .subscribe({
        next: (r) => {
          this.report = r;
          this.buildChart(r);
          this.loading = false;
        },
        error: (err) => {
          this.report = null;
          this.bars = [];
          this.loading = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  successRatePct(): number {
    return Math.round((this.report?.successRate ?? 0) * 1000) / 10;
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? `${id.slice(0, 8)}…` : id;
  }

  private buildChart(r: TransactionReport): void {
    this.maxCompletedAmount = Math.max(...r.daily.map((d) => d.completedAmount), 1);
    this.bars = r.daily.map((d) => ({
      day: d.day,
      heightPct: Math.max((d.completedAmount / this.maxCompletedAmount) * 100, d.totalCount > 0 ? 2 : 0),
      hasFailed: d.failedCount > 0,
      tooltip: `${d.day}: ${d.completedCount}/${d.totalCount} ${this.i18n.instant('ADMIN.REPORT_TOOLTIP_OK')}`
        + (d.failedCount ? `, ${d.failedCount} ${this.i18n.instant('ADMIN.REPORT_TOOLTIP_FAILED')}` : ''),
    }));
  }
}
