import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { LangSwitcherComponent } from '../../../shared/components/lang-switcher/lang-switcher.component';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthApiService);
  private readonly i18n = inject(TranslateService);

  loading = false;
  done = false;
  error: string | null = null;

  form = this.fb.nonNullable.group({
    usernameOrEmail: ['', Validators.required],
    channel: ['EMAIL' as 'EMAIL' | 'SMS'],
    note: [''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.error = null;
    const v = this.form.getRawValue();
    this.api
      .createPasswordResetTicket({
        usernameOrEmail: v.usernameOrEmail.trim(),
        channel: v.channel,
        note: v.note?.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.loading = false;
          this.done = true;
        },
        error: (err) => {
          this.loading = false;
          this.error =
            err instanceof HttpErrorResponse
              ? resolveHttpErrorMessage(err, this.i18n)
              : this.i18n.instant('AUTH.FORGOT_FAIL');
        },
      });
  }
}
