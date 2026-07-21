import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
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
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { Transfer } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { TransferDetailDialogComponent } from '../../../shared/components/transfer-detail-dialog/transfer-detail-dialog.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { TransfersActions } from '../../../store/transfers/transfers.actions';
import {
  selectTransferHistory,
  selectTransferLoading,
  selectTransferPageMeta,
} from '../../../store/transfers/transfers.selectors';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
})
export class HistoryComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly api = inject(BankApiService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  rows$ = this.store.select(selectTransferHistory);
  loading$ = this.store.select(selectTransferLoading);
  meta$ = this.store.select(selectTransferPageMeta);
  cols = ['createdAt', 'description', 'amount', 'feeAmount', 'status', 'transactionId', 'actions'];
  openingId: string | null = null;
  pageIndex = 0;
  pageSize = 10;

  readonly statusOptions = [
    '',
    'COMPLETED',
    'FAILED',
    'PENDING',
    'DEBITED',
    'COMPENSATING',
    'COMPENSATED',
  ];

  filter = this.fb.nonNullable.group({
    status: [''],
    from: [''],
    to: [''],
  });

  ngOnInit(): void {
    this.reload();
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.reload();
  }

  resetFilters(): void {
    this.filter.reset({ status: '', from: '', to: '' });
    this.pageIndex = 0;
    this.reload();
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.reload();
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
      error: () => {
        this.openingId = null;
        this.toast.error(this.i18n.instant('TRANSFER_DETAIL.LOAD_FAIL'));
      },
    });
  }

  private reload(): void {
    const f = this.filter.getRawValue();
    this.store.dispatch(
      TransfersActions.loadHistory({
        page: this.pageIndex,
        size: this.pageSize,
        status: f.status || undefined,
        from: this.toInstantStart(f.from),
        to: this.toInstantEnd(f.to),
      }),
    );
  }

  private toInstantStart(date: string): string | undefined {
    if (!date) return undefined;
    return `${date}T00:00:00.000Z`;
  }

  private toInstantEnd(date: string): string | undefined {
    if (!date) return undefined;
    return `${date}T23:59:59.999Z`;
  }
}
