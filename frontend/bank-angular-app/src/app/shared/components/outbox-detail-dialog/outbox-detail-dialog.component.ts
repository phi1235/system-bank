import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { OutboxEvent } from '../../../core/models/domain.model';
import { ToastService } from '../../../core/services/toast.service';
import { copyText } from '../../../core/utils/transfer-receipt.util';
import { EnumLabelPipe } from '../../pipes/enum-label.pipe';

export interface OutboxDetailDialogData {
  event: OutboxEvent;
}

@Component({
  selector: 'app-outbox-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
    EnumLabelPipe,
  ],
  templateUrl: './outbox-detail-dialog.component.html',
  styleUrl: './outbox-detail-dialog.component.scss',
})
export class OutboxDetailDialogComponent {
  private readonly data = inject<OutboxDetailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<OutboxDetailDialogComponent>);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  readonly event = this.data.event;

  get prettyPayload(): string {
    const raw = this.event?.payload;
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
      this.toast.error(this.i18n.instant('ADMIN.OUTBOX_COPY_FAIL'));
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
