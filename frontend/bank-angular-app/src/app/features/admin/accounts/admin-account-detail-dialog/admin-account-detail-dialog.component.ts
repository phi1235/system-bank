import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Account } from '../../../../core/models/domain.model';
import { MoneyVndPipe } from '../../../../shared/pipes/money-vnd.pipe';
import { copyText } from '../../../../core/utils/transfer-receipt.util';
import { ToastService } from '../../../../core/services/toast.service';
import { AdminTopUpDialogComponent } from '../admin-top-up-dialog/admin-top-up-dialog.component';

export interface AdminAccountDetailDialogData {
  account: Account;
  canTopUp: boolean;
}

@Component({
  selector: 'app-admin-account-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
    MoneyVndPipe,
  ],
  templateUrl: './admin-account-detail-dialog.component.html',
  styleUrl: './admin-account-detail-dialog.component.scss',
})
export class AdminAccountDetailDialogComponent {
  readonly data = inject<AdminAccountDetailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AdminAccountDetailDialogComponent>);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  async copy(val: string, label: string): Promise<void> {
    if (!val) return;
    const ok = await copyText(val);
    this.toast[ok ? 'success' : 'error'](
      ok ? `Đã sao chép ${label}` : `Không thể sao chép ${label}`
    );
  }

  openTopUp(): void {
    this.dialogRef.close();
    this.dialog.open(AdminTopUpDialogComponent, {
      data: { account: this.data.account },
      width: '460px',
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}
