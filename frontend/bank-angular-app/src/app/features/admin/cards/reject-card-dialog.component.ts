import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminCard } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';

/** Reject a card request — the reason is mandatory and is shown to the customer. */
@Component({
  selector: 'app-reject-card-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'ADMIN.CARDS_REJECT_TITLE' | translate }}</h2>
    <mat-dialog-content>
      <p class="hint">
        {{ 'ADMIN.CARDS_REJECT_HINT' | translate: { owner: data.ownerName || data.userId, account: data.accountNumber || '—' } }}
      </p>
      <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
        <mat-label>{{ 'ADMIN.CARDS_REJECT_REASON' | translate }}</mat-label>
        <textarea matInput [(ngModel)]="reason" rows="3" maxlength="255" required></textarea>
        <mat-hint align="end">{{ reason.length }}/255</mat-hint>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close type="button">{{ 'COMMON.CANCEL' | translate }}</button>
      <button mat-flat-button color="warn" type="button" (click)="reject()"
              [disabled]="!reason.trim() || saving">
        {{ 'ADMIN.CARDS_REJECT_BTN' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full { width: 100%; }
      .hint { font-size: 13px; color: rgba(0, 0, 0, 0.6); margin: 0 0 12px; }
    `,
  ],
})
export class RejectCardDialogComponent {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly ref = inject(MatDialogRef<RejectCardDialogComponent>);
  readonly data: AdminCard = inject(MAT_DIALOG_DATA);

  reason = '';
  saving = false;

  reject(): void {
    if (!this.reason.trim() || this.saving) {
      return;
    }
    this.saving = true;
    this.api.adminRejectCard(this.data.id, this.reason.trim()).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('ADMIN.CARDS_REJECTED'));
        this.ref.close(true);
      },
      error: (err) => {
        this.saving = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }
}
