import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Transfer } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { TransferDetailDialogComponent } from '../../../shared/components/transfer-detail-dialog/transfer-detail-dialog.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';

@Component({
  selector: 'app-admin-transfers',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
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

  rows: Transfer[] = [];
  status = '';
  cols = ['createdAt', 'amount', 'status', 'fromAccountId', 'toAccountNumber', 'transactionId', 'actions'];
  openingId: string | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.adminTransfers(0, 50, this.status || undefined).subscribe({
      next: (p) => (this.rows = p.items || []),
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
      error: () => {
        this.openingId = null;
        this.toast.error(this.i18n.instant('TRANSFER_DETAIL.LOAD_FAIL'));
      },
    });
  }
}
