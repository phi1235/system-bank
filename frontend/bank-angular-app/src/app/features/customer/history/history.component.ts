import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
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
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatDialogModule,
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

  rows$ = this.store.select(selectTransferHistory);
  loading$ = this.store.select(selectTransferLoading);
  meta$ = this.store.select(selectTransferPageMeta);
  cols = ['createdAt', 'description', 'amount', 'feeAmount', 'status', 'transactionId', 'actions'];
  openingId: string | null = null;

  ngOnInit(): void {
    this.store.dispatch(TransfersActions.loadHistory({ page: 0, size: 10 }));
  }

  page(e: PageEvent): void {
    this.store.dispatch(TransfersActions.loadHistory({ page: e.pageIndex, size: e.pageSize }));
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
}
