import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DepositProduct } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';

/** Edit one deposit product's rates/minimum/availability. Existing contracts keep snapshots. */
@Component({
  selector: 'app-edit-deposit-product-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'ADMIN.DEPOSITS_EDIT_TITLE' | translate: { code: data.code } }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'ADMIN.DEPOSITS_EDIT_HINT' | translate }}</p>
      <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
        <mat-label>{{ 'ADMIN.DEPOSITS_EDIT_RATE' | translate }}</mat-label>
        <input matInput type="number" [(ngModel)]="rateBps" min="0" max="3000" />
        <mat-hint>{{ rateBps / 100 }}%/{{ 'CUSTOMER.WEALTH_YEAR' | translate }}</mat-hint>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
        <mat-label>{{ 'ADMIN.DEPOSITS_EDIT_EARLY_RATE' | translate }}</mat-label>
        <input matInput type="number" [(ngModel)]="earlyRateBps" min="0" max="3000" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
        <mat-label>{{ 'ADMIN.DEPOSITS_EDIT_MIN' | translate }}</mat-label>
        <input matInput type="number" [(ngModel)]="minAmount" min="1" />
      </mat-form-field>
      <mat-slide-toggle [(ngModel)]="active">{{ 'ADMIN.DEPOSITS_EDIT_ACTIVE' | translate }}</mat-slide-toggle>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close type="button">{{ 'COMMON.CANCEL' | translate }}</button>
      <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="saving">
        {{ 'COMMON.SAVE' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full { width: 100%; margin-bottom: 12px; }
      .hint { font-size: 13px; color: rgba(0, 0, 0, 0.6); margin: 0 0 12px; }
      mat-slide-toggle { margin-bottom: 8px; }
    `,
  ],
})
export class EditDepositProductDialogComponent {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly ref = inject(MatDialogRef<EditDepositProductDialogComponent>);
  readonly data: DepositProduct = inject(MAT_DIALOG_DATA);

  rateBps = this.data.rateBps;
  earlyRateBps = this.data.earlyRateBps;
  minAmount = this.data.minAmount;
  active = this.data.active;
  saving = false;

  save(): void {
    this.saving = true;
    this.api
      .adminUpdateDepositProduct(this.data.code, {
        rateBps: this.rateBps,
        earlyRateBps: this.earlyRateBps,
        minAmount: this.minAmount,
        active: this.active,
      })
      .subscribe({
        next: (updated) => {
          this.toast.success(this.i18n.instant('ADMIN.DEPOSITS_EDIT_DONE', { code: updated.code }));
          this.ref.close(updated);
        },
        error: (err) => {
          this.saving = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }
}
