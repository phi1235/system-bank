import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuditLog } from '../../../core/models/domain.model';
import { ToastService } from '../../../core/services/toast.service';
import { copyText } from '../../../core/utils/transfer-receipt.util';

export interface AuditDetailDialogData {
  log: AuditLog;
}

@Component({
  selector: 'app-audit-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './audit-detail-dialog.component.html',
  styleUrl: './audit-detail-dialog.component.scss',
})
export class AuditDetailDialogComponent {
  private readonly data = inject<AuditDetailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AuditDetailDialogComponent>);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  readonly log = this.data.log;

  get prettyMetadata(): string {
    const raw = this.log?.metadata;
    if (!raw) {
      return '';
    }
    const trimmed = raw.trim();
    if (!trimmed) {
      return '';
    }
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2);
    } catch {
      return raw;
    }
  }

  async copyField(value: string | null | undefined, okKey: string): Promise<void> {
    if (!value) {
      return;
    }
    const ok = await copyText(value);
    if (ok) {
      this.toast.success(this.i18n.instant(okKey));
    } else {
      this.toast.error(this.i18n.instant('ADMIN.AUDIT_COPY_FAIL'));
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
