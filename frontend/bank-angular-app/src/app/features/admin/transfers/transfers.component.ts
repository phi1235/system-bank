import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Transfer } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { TransferDetailDialogComponent } from '../../../shared/components/transfer-detail-dialog/transfer-detail-dialog.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';
import { exportToCsv } from '../../../core/utils/csv-export.util';

@Component({
  selector: 'app-admin-transfers',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatIconModule,
    MatTooltipModule,
    MatDialogModule,
    PageHeaderComponent,
    MoneyVndPipe,
    TransferStatusPipe,
    TranslateModule,
  ],
  templateUrl: './transfers.component.html',
  styleUrl: './transfers.component.scss',
})
export class AdminTransfersComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);

  rows: Transfer[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  loading = false;
  status = '';
  transferId = '';
  q = '';
  from = '';
  to = '';
  cols = [
    'createdAt',
    'amount',
    'status',
    'fromAccountId',
    'toAccountNumber',
    'transactionId',
    'actions',
  ];
  openingId: string | null = null;

  readonly statusOptions = [
    '',
    'PENDING',
    'DEBITED',
    'COMPLETED',
    'FAILED',
    'COMPENSATING',
    'COMPENSATED',
  ];

  ngOnInit(): void {
    const st = (this.route.snapshot.queryParamMap.get('status') || '').toUpperCase();
    if (this.statusOptions.includes(st)) {
      this.status = st;
    }
    this.load();
  }

  get hasActiveFilters(): boolean {
    return !!(
      this.status ||
      this.transferId.trim() ||
      this.q.trim() ||
      this.from ||
      this.to
    );
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.status = '';
    this.transferId = '';
    this.q = '';
    this.from = '';
    this.to = '';
    this.pageIndex = 0;
    this.load();
  }

  exportCsv(): void {
    if (!this.rows.length) return;
    exportToCsv(
      `transfers_${new Date().toISOString().slice(0, 10)}`,
      [
        { key: 'createdAt', label: 'Time' },
        { key: 'id', label: 'Transaction ID' },
        { key: 'fromAccountId', label: 'From Account' },
        { key: 'toAccountNumber', label: 'To Account' },
        { key: 'amount', label: 'Amount' },
        { key: 'currency', label: 'Currency' },
        { key: 'status', label: 'Status' },
        { key: 'description', label: 'Description' },
      ],
      this.rows as unknown as Record<string, unknown>[],
    );
    this.toast.success(this.i18n.instant('COMMON.EXPORT_SUCCESS'));
  }

  load(): void {
    this.loading = true;
    this.api
      .adminTransfers(this.pageIndex, this.pageSize, {
        status: this.status || undefined,
        transferId: this.transferId.trim() || undefined,
        q: this.q.trim() || undefined,
        from: this.toIsoStart(this.from),
        to: this.toIsoEnd(this.to),
      })
      .subscribe({
        next: (p) => {
          this.rows = p.items || [];
          this.totalElements = p.totalElements ?? this.rows.length;
          this.loading = false;
        },
        error: (err) => {
          this.rows = [];
          this.totalElements = 0;
          this.loading = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  openDetail(row: Transfer): void {
    if (!row?.transactionId || this.openingId) {
      return;
    }
    this.openingId = row.transactionId;
    this.api.getTransferDetail(row.transactionId).subscribe({
      next: (detail) => {
        this.openingId = null;
        this.dialog.open(TransferDetailDialogComponent, {
          data: { detail },
          width: '560px',
          maxWidth: '95vw',
        });
      },
      error: (err) => {
        this.openingId = null;
        this.toast.error(
          resolveHttpErrorMessage(err, this.i18n) ||
            this.i18n.instant('TRANSFER_DETAIL.LOAD_FAIL'),
        );
      },
    });
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? `${id.slice(0, 8)}…` : id;
  }

  private toIsoStart(date: string): string | undefined {
    const d = (date || '').trim();
    if (!d) {
      return undefined;
    }
    return `${d}T00:00:00.000Z`;
  }

  private toIsoEnd(date: string): string | undefined {
    const d = (date || '').trim();
    if (!d) {
      return undefined;
    }
    return `${d}T23:59:59.999Z`;
  }
}
