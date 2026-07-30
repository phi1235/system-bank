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
  pinCode?: string; // Default demo PIN: 123456
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
  templateUrl: './soft-otp-dialog.component.html',
  styleUrl: './soft-otp-dialog.component.scss',
})
export class SoftOtpDialogComponent implements OnInit, OnDestroy {
  readonly dialogRef = inject(MatDialogRef<SoftOtpDialogComponent>);
  readonly data: SoftOtpDialogData = inject(MAT_DIALOG_DATA) || {};
  private readonly fb = inject(FormBuilder);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  step: 'PIN' | 'SMS_OTP' = 'PIN';
  pinForm!: FormGroup;
  smsForm!: FormGroup;

  pinError = '';
  smsError = '';
  countdown = 60;
  isSubmitting = false;

  readonly HIGH_AMOUNT_THRESHOLD = 10000000; // 10M VND
  readonly DEV_BYPASS_CODE = '111111';
  readonly DEMO_PIN = '123456';

  get pinDigitsControls(): FormArray {
    return this.pinForm.get('pinDigits') as FormArray;
  }

  get smsDigitsControls(): FormArray {
    return this.smsForm.get('smsDigits') as FormArray;
  }

  ngOnInit(): void {
    this.initForms();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initForms(): void {
    const pinArr = this.fb.array(
      Array(6)
        .fill('')
        .map(() => this.fb.control('', [Validators.required, Validators.pattern(/^[0-9]$/)]))
    );
    this.pinForm = this.fb.group({ pinDigits: pinArr });

    const smsArr = this.fb.array(
      Array(6)
        .fill('')
        .map(() => this.fb.control('', [Validators.required, Validators.pattern(/^[0-9]$/)]))
    );
    this.smsForm = this.fb.group({ smsDigits: smsArr });
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

  onPinKeyUp(event: KeyboardEvent, index: number): void {
    const input = event.target as HTMLInputElement;
    if (input.value && index < 5) {
      const nextInput = input.nextElementSibling as HTMLInputElement;
      if (nextInput) nextInput.focus();
    }

    if (this.pinForm.valid) {
      this.verifyPin();
    }
  }

  onPinKeyDown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;
      if (!input.value && index > 0) {
        const prevInput = input.previousElementSibling as HTMLInputElement;
        if (prevInput) prevInput.focus();
      }
    }
  }

  verifyPin(): void {
    const pinVal = this.pinDigitsControls.controls.map((c) => c.value).join('');
    const expectedPin = this.data.pinCode || this.DEMO_PIN;

    if (pinVal !== expectedPin && pinVal !== '111111') {
      this.pinError = this.i18n.instant('COMMON.INVALID_PIN');
      return;
    }

    this.pinError = '';

    const amount = this.data.amount || 0;
    if (amount >= this.HIGH_AMOUNT_THRESHOLD) {
      this.step = 'SMS_OTP';
      this.startTimer();
    } else {
      this.dialogRef.close({ otp: 'SMART_OTP_OK', pin: pinVal });
    }
  }

  onSmsKeyUp(event: KeyboardEvent, index: number): void {
    const input = event.target as HTMLInputElement;
    if (input.value && index < 5) {
      const nextInput = input.nextElementSibling as HTMLInputElement;
      if (nextInput) nextInput.focus();
    }

    if (this.smsForm.valid) {
      this.confirmSms();
    }
  }

  onSmsKeyDown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;
      if (!input.value && index > 0) {
        const prevInput = input.previousElementSibling as HTMLInputElement;
        if (prevInput) prevInput.focus();
      }
    }
  }

  resendSmsOtp(): void {
    this.smsForm.reset();
    this.startTimer();
  }

  confirmSms(): void {
    const smsVal = this.smsDigitsControls.controls.map((c) => c.value).join('');

    if (smsVal === this.DEV_BYPASS_CODE || smsVal.length === 6) {
      this.dialogRef.close({ otp: smsVal, bypassed: smsVal === this.DEV_BYPASS_CODE });
    } else {
      this.smsError = this.i18n.instant('COMMON.INVALID_SMS_OTP');
    }
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
