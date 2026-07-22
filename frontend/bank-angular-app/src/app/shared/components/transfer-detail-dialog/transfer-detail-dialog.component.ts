import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { TransferDetail } from '../../../core/models/domain.model';
import { FriendlyTransferErrorPipe } from '../../pipes/friendly-transfer-error.pipe';
import { TransferStatusPipe } from '../../pipes/transfer-status.pipe';
import { MoneyVndPipe } from '../../pipes/money-vnd.pipe';

export interface TransferDetailDialogData {
  detail: TransferDetail;
}

@Component({
  selector: 'app-transfer-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    TranslateModule,
    MoneyVndPipe,
    FriendlyTransferErrorPipe,
    TransferStatusPipe,
  ],
  templateUrl: './transfer-detail-dialog.component.html',
  styleUrl: './transfer-detail-dialog.component.scss',
})
export class TransferDetailDialogComponent {
  readonly data = inject<TransferDetailDialogData>(MAT_DIALOG_DATA);
  readonly detail = this.data.detail;
}
