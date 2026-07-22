import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { RbacStaffUser } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { copyText } from '../../../core/utils/transfer-receipt.util';

export interface UserDetailDialogData {
  user: RbacStaffUser;
}

@Component({
  selector: 'app-user-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './user-detail-dialog.component.html',
  styleUrl: './user-detail-dialog.component.scss',
})
export class UserDetailDialogComponent {
  private readonly data = inject<UserDetailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<UserDetailDialogComponent>);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  readonly user = this.data.user;

  get rolesText(): string {
    return (this.user.roles || []).join(', ') || '—';
  }

  get permissionsText(): string {
    const perms = this.user.permissions || [];
    return perms.length ? perms.join(', ') : '—';
  }

  async copyField(value: string | null | undefined, okKey: string): Promise<void> {
    if (!value) {
      return;
    }
    const ok = await copyText(value);
    if (ok) {
      this.toast.success(this.i18n.instant(okKey));
    } else {
      this.toast.error(this.i18n.instant('ADMIN.USERS_COPY_FAIL'));
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
