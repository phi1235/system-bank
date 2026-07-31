import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { filter, switchMap } from 'rxjs';
import { OutboxCounts, OutboxEvent } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { OutboxDetailDialogComponent } from '../../../shared/components/outbox-detail-dialog/outbox-detail-dialog.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { exportToCsv } from '../../../core/utils/csv-export.util';

@Component({
  selector: 'app-admin-outbox',
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
    TranslateModule,
    EnumLabelPipe,
  ],
  templateUrl: './outbox.component.html',
  styleUrl: './outbox.component.scss',
})
export class AdminOutboxComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);
  private readonly i18n = inject(TranslateService);

  rows: OutboxEvent[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  counts: OutboxCounts | null = null;
  status = 'DEAD';
  eventType = '';
  eventId = '';
  aggregateId = '';
  q = '';
  from = '';
  to = '';
  loading = false;
  replayingId: string | null = null;
  openingId: string | null = null;
  cols = [
    'createdAt',
    'status',
    'eventType',
    'aggregateId',
    'attemptCount',
    'lastError',
    'actions',
  ];

  readonly statusOptions = ['DEAD', 'PENDING', 'PUBLISHED'];

  ngOnInit(): void {
    this.load();
  }

  get hasActiveFilters(): boolean {
    return !!(
      this.eventType.trim() ||
      this.eventId.trim() ||
      this.aggregateId.trim() ||
      this.q.trim() ||
      this.from ||
      this.to ||
      (this.status && this.status !== 'DEAD')
    );
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.status = 'DEAD';
    this.eventType = '';
    this.eventId = '';
    this.aggregateId = '';
    this.q = '';
    this.from = '';
    this.to = '';
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.adminOutboxCounts().subscribe({
      next: (c) => (this.counts = c),
      error: () => (this.counts = null),
    });
    this.api
      .adminOutboxList(this.pageIndex, this.pageSize, {
        status: this.status || undefined,
        eventType: this.eventType.trim() || undefined,
        eventId: this.eventId.trim() || undefined,
        aggregateId: this.aggregateId.trim() || undefined,
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

  exportCsv(): void {
    if (!this.rows.length) return;
    exportToCsv(
      `outbox_events_${new Date().toISOString().slice(0, 10)}`,
      [
        { key: 'createdAt', label: 'Time' },
        { key: 'id', label: 'Event ID' },
        { key: 'aggregateType', label: 'Aggregate Type' },
        { key: 'aggregateId', label: 'Aggregate ID' },
        { key: 'eventType', label: 'Event Type' },
        { key: 'status', label: 'Status' },
        { key: 'retryCount', label: 'Retry Count' },
      ],
      this.rows as unknown as Record<string, unknown>[],
    );
    this.toast.success(this.i18n.instant('COMMON.EXPORT_SUCCESS'));
  }

  openDetail(row: OutboxEvent): void {
    if (!row?.id || this.openingId) {
      return;
    }
    this.openingId = row.id;
    this.api.adminOutboxDetail(row.id).subscribe({
      next: (event) => {
        this.openingId = null;
        this.dialog.open(OutboxDetailDialogComponent, {
          data: { event },
          width: '640px',
          maxWidth: '95vw',
        });
      },
      error: () => {
        this.openingId = null;
        // Interceptor toasts; fall back to list row without payload.
        this.dialog.open(OutboxDetailDialogComponent, {
          data: { event: row },
          width: '640px',
          maxWidth: '95vw',
        });
      },
    });
  }

  replay(row: OutboxEvent, event?: Event): void {
    event?.stopPropagation();
    if (row.status !== 'DEAD' || this.replayingId) {
      return;
    }
    const data: ConfirmDialogData = {
      title: this.i18n.instant('ADMIN.OUTBOX_REPLAY_TITLE'),
      message: this.i18n.instant('ADMIN.OUTBOX_REPLAY_MSG', {
        id: row.id.slice(0, 8),
        type: row.eventType,
      }),
      confirmLabel: this.i18n.instant('ADMIN.OUTBOX_REPLAY'),
      destructive: false,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.replayingId = row.id;
          return this.api.adminOutboxReplay(row.id);
        }),
      )
      .subscribe({
        next: () => {
          this.replayingId = null;
          this.toast.success(this.i18n.instant('ADMIN.OUTBOX_REPLAY_OK'));
          this.load();
        },
        error: (err) => {
          this.replayingId = null;
          this.toast.error(
            resolveHttpErrorMessage(err, this.i18n) ||
              this.i18n.instant('ADMIN.OUTBOX_REPLAY_FAIL'),
          );
        },
      });
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? id.slice(0, 8) + '…' : id;
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
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
