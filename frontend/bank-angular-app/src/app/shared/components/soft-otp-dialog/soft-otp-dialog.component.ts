import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, interval, takeUntil } from 'rxjs';

export interface SoftOtpDialogData {
  title?: string;
  amount?: number;
  recipientName?: string;
  recipientAccount?: string;
}

@Component({
  selector: 'app-soft-otp-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    TranslateModule,
  ],
  template: `
    <div class="otp-dialog-container p-4">
      <h2 mat-dialog-title class="dialog-title text-center font-weight-bold">
        <mat-icon class="align-middle color-primary">lock</mat-icon>
        {{ (data.title || 'COMMON.SOFT_OTP_VERIFY') | translate }}
      </h2>

      <mat-dialog-content class="my-3">
        <p class="text-muted text-center mb-3">
          {{ 'COMMON.SOFT_OTP_HINT' | translate }}
        </p>

        <div *ngIf="data.amount" class="transaction-summary card bg-light p-3 mb-4 rounded text-center">
          <div class="small text-secondary">{{ 'COMMON.TRANSACTION_AMOUNT' | translate }}</div>
          <div class="h4 font-weight-bold text-success my-1">{{ data.amount | number }} VND</div>
          <div *ngIf="data.recipientName" class="small text-muted">
            {{ 'COMMON.RECIPIENT' | translate }}: <strong>{{ data.recipientName }}</strong> ({{ data.recipientAccount }})
          </div>
        </div>

        <form [formGroup]="otpForm" class="d-flex justify-content-center gap-2 my-4">
          <div formArrayName="digits" class="d-flex gap-2">
            <input
              *ngFor="let control of digitsControls.controls; let i = index"
              type="text"
              maxlength="1"
              class="form-control text-center otp-input-box"
              [formControlName]="i"
              (keyup)="onKeyUp($event, i)"
              (keydown)="onKeyDown($event, i)"
              #otpInput
            />
          </div>
        </form>

        <div class="text-center mt-3">
          <span *ngIf="countdown > 0" class="text-secondary small">
            {{ 'COMMON.OTP_EXPIRES_IN' | translate }}: <strong class="text-danger">{{ countdown }}s</strong>
          </span>
          <span *ngIf="countdown === 0">
            <button mat-button color="primary" (click)="resendOtp()">
              <mat-icon>refresh</mat-icon>
              {{ 'COMMON.RESEND_OTP' | translate }}
            </button>
          </span>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end" class="gap-2">
        <button mat-button (click)="cancel()">{{ 'COMMON.CANCEL' | translate }}</button>
        <button
          mat-raised-button
          color="primary"
          [disabled]="otpForm.invalid || isSubmitting"
          (click)="confirm()"
        >
          {{ 'COMMON.CONFIRM' | translate }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [
    `
      .otp-dialog-container {
        min-width: 320px;
        max-width: 440px;
      }
      .otp-input-box {
        width: 44px;
        height: 52px;
        font-size: 1.5rem;
        font-weight: 700;
        text-align: center;
        border-radius: 8px;
        border: 2px solid #ced4da;
      }
      .otp-input-box:focus {
        border-color: #0d6efd;
        box-shadow: 0 0 0 0.25rem rgba(13, 110, 253, 0.25);
      }
    `,
  ],
})
export class SoftOtpDialogComponent implements OnInit, OnDestroy {
  readonly dialogRef = inject(MatDialogRef<SoftOtpDialogComponent>);
  readonly data: SoftOtpDialogData = inject(MAT_DIALOG_DATA) || {};
  private readonly fb = inject(FormBuilder);
  private readonly destroy$ = new Subject<void>();

  otpForm!: FormGroup;
  countdown = 60;
  isSubmitting = false;

  get digitsControls(): FormArray {
    return this.otpForm.get('digits') as FormArray;
  }

  ngOnInit(): void {
    this.initForm();
    this.startTimer();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initForm(): void {
    const arr = this.fb.array(
      Array(6)
        .fill('')
        .map(() => this.fb.control('', [Validators.required, Validators.pattern(/^[0-9]$/)]))
    );
    this.otpForm = this.fb.group({ digits: arr });
  }

  private startTimer(): void {
    this.countdown = 60;
    interval(1000)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this.countdown > 0) {
          this.countdown--;
        }
      });
  }

  onKeyUp(event: KeyboardEvent, index: number): void {
    const input = event.target as HTMLInputElement;
    if (input.value && index < 5) {
      const nextInput = input.nextElementSibling as HTMLInputElement;
      if (nextInput) {
        nextInput.focus();
      }
    }
  }

  onKeyDown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;
      if (!input.value && index > 0) {
        const prevInput = input.previousElementSibling as HTMLInputElement;
        if (prevInput) {
          prevInput.focus();
        }
      }
    }
  }

  resendOtp(): void {
    this.otpForm.reset();
    this.startTimer();
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  confirm(): void {
    if (this.otpForm.valid) {
      const otpValue = this.digitsControls.controls.map((c) => c.value).join('');
      this.dialogRef.close({ otp: otpValue });
    }
  }
}
