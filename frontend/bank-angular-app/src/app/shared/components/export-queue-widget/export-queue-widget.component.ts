import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ExportQueueService, ExportTask } from '../../../core/services/export-queue.service';
import { TokenService } from '../../../core/services/token.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-export-queue-widget',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatDialogModule,
    TranslateModule,
  ],
  templateUrl: './export-queue-widget.component.html',
  styleUrl: './export-queue-widget.component.scss',
})
export class ExportQueueWidgetComponent {
  readonly exportQueue = inject(ExportQueueService);
  readonly tokenService = inject(TokenService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  get isLoggedIn(): boolean {
    return this.tokenService.hasToken();
  }

  isExpanded = false;

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  resumeTask(task: ExportTask, event: Event): void {
    event.stopPropagation();
    this.exportQueue.resumeTask(task.id);
  }

  cancelTask(task: ExportTask, event: Event): void {
    event.stopPropagation();
    const data: ConfirmDialogData = {
      title: this.i18n.instant('COMMON.EXPORT_CANCEL_TITLE') || 'Xác nhận hủy xuất file',
      message: this.i18n.instant('COMMON.EXPORT_CANCEL_MSG') || 'Bạn có chắc chắn muốn hủy tác vụ xuất file CSV này không?',
      confirmText: this.i18n.instant('COMMON.YES') || 'Có',
      cancelText: this.i18n.instant('COMMON.NO') || 'Không',
      danger: true,
    };

    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.exportQueue.cancelTask(task.id);
          this.toast.info(this.i18n.instant('COMMON.EXPORT_CANCELLED') || 'Đã hủy tác vụ xuất file CSV.');
        }
      });
  }

  downloadTask(task: ExportTask, event: Event): void {
    event.stopPropagation();
    this.exportQueue.downloadTask(task.id);
  }

  removeTask(task: ExportTask, event: Event): void {
    event.stopPropagation();
    this.exportQueue.removeTask(task.id);
  }
}
