import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { Store } from '@ngrx/store';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { isStaffUser } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { AuthActions } from '../../../store/auth/auth.actions';
import { selectUser } from '../../../store/auth/auth.selectors';
import { LangSwitcherComponent } from '../../../shared/components/lang-switcher/lang-switcher.component';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss',
})
export class ChangePasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthApiService);
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  loading = false;
  error: string | null = null;
  user$ = this.store.select(selectUser);

  form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.newPassword !== v.confirmPassword) {
      this.error = this.i18n.instant('AUTH.PWD_MISMATCH');
      return;
    }
    this.loading = true;
    this.error = null;
    this.api
      .changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword })
      .subscribe({
        next: () => {
          this.loading = false;
          this.toast.success(this.i18n.instant('AUTH.PWD_CHANGED_OK'));
          // Reload me then route by role
          this.api.me().subscribe({
            next: (user) => {
              this.store.dispatch(AuthActions.loadMeSuccess({ user }));
              const roles = user.roles || [];
              const perms = user.permissions || [];
              const staff = !!user.staff || isStaffUser(roles, perms);
              if (staff && !roles.includes('CUSTOMER')) {
                this.router.navigateByUrl('/admin');
              } else if (roles.includes('CUSTOMER')) {
                this.router.navigateByUrl('/customer/home');
              } else if (staff) {
                this.router.navigateByUrl('/admin');
              } else {
                this.router.navigateByUrl('/auth/login');
              }
            },
            error: () => this.router.navigateByUrl('/auth/login'),
          });
        },
        error: (err) => {
          this.loading = false;
          this.error =
            err instanceof HttpErrorResponse
              ? resolveHttpErrorMessage(err, this.i18n)
              : this.i18n.instant('AUTH.PWD_CHANGE_FAIL');
        },
      });
  }

  logout(): void {
    this.store.dispatch(AuthActions.logout());
  }
}
