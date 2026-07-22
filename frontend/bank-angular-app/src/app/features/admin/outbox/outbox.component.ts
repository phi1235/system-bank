import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';
import { OutboxCounts, OutboxEvent } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

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
  loading = false;
  replayingId: string | null = null;
  cols = ['createdAt', 'status', 'eventType', 'aggregateId', 'attemptCount', 'lastError', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.adminOutboxCounts().subscribe({
      next: (c) => (this.counts = c),
      error: () => (this.counts = null),
    });
    this.api.adminOutboxList(this.pageIndex, this.pageSize, this.status || undefined).subscribe({
      next: (p) => {
        this.rows = p.items || [];
        this.totalElements = p.totalElements ?? this.rows.length;
        this.loading = false;
      },
      error: () => {
        this.rows = [];
        this.totalElements = 0;
        this.loading = false;
        this.toast.error(this.i18n.instant('ADMIN.OUTBOX_LOAD_FAIL'));
      },
    });
  }

  replay(row: OutboxEvent): void {
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
        error: () => {
          this.replayingId = null;
          this.toast.error(this.i18n.instant('ADMIN.OUTBOX_REPLAY_FAIL'));
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

}
