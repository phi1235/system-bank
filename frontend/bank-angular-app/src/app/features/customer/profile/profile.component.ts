import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { filter, switchMap } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { selectHasPermission, selectUser } from '../../../store/auth/auth.selectors';
import { AuthActions } from '../../../store/auth/auth.actions';
import { CustomerProfile } from '../../../core/models/domain.model';
import { AuthSession, MfaSetupResponse } from '../../../core/models/auth.model';
import { PERMISSIONS } from '../../../core/services/rbac.util';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly bank = inject(BankApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);

  user$ = this.store.select(selectUser);
  canEdit$ = this.store.select(selectHasPermission(PERMISSIONS.IB_PROFILE_EDIT));
  canMfa$ = this.store.select(selectHasPermission(PERMISSIONS.IB_PROFILE_MFA));

  profile: CustomerProfile | null = null;
  needsCreate = false;
  mfaSetup: MfaSetupResponse | null = null;
  loading = false;
  changingPassword = false;

  sessions: AuthSession[] = [];
  sessionsLoading = false;
  revokingId: string | null = null;
  revokingOthers = false;

  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    phone: [''],
    email: ['', Validators.email],
    nationalId: [''],
    address: [''],
  });

  mfaForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
  });

  ngOnInit(): void {
    this.load();
    this.loadSessions();
  }

  load(): void {
    this.loading = true;
    this.bank.getProfile().subscribe({
      next: (p) => {
        this.profile = p;
        this.needsCreate = false;
        this.form.patchValue({
          fullName: p.fullName || '',
          phone: p.phone || '',
          address: p.address || '',
          email: p.email || '',
        });
        this.loading = false;
      },
      error: () => {
        this.needsCreate = true;
        this.loading = false;
      },
    });
  }

  loadSessions(): void {
    this.sessionsLoading = true;
    this.authApi.listSessions().subscribe({
      next: (list) => {
        this.sessions = list || [];
        this.sessionsLoading = false;
      },
      error: () => {
        this.sessions = [];
        this.sessionsLoading = false;
      },
    });
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    if (this.needsCreate) {
      this.bank
        .createProfile({
          fullName: v.fullName,
          phone: v.phone || undefined,
          email: v.email || undefined,
          nationalId: v.nationalId || undefined,
          address: v.address || undefined,
        })
        .subscribe({
          next: (p) => {
            this.profile = p;
            this.needsCreate = false;
            this.toast.success(this.i18n.instant('CUSTOMER.PROFILE_CREATE_OK'));
          },
        });
    } else {
      this.bank
        .updateProfile({
          fullName: v.fullName,
          phone: v.phone || undefined,
          email: v.email?.trim() ? v.email.trim() : '',
          address: v.address || undefined,
        })
        .subscribe({
          next: (p) => {
            this.profile = p;
            this.toast.success(this.i18n.instant('CUSTOMER.PROFILE_UPDATE_OK'));
          },
        });
    }
  }

  startMfa(): void {
    this.authApi.mfaSetup().subscribe({
      next: (s) => {
        this.mfaSetup = s;
        this.toast.info(this.i18n.instant('CUSTOMER.MFA_SETUP_HINT'));
      },
    });
  }

  enableMfa(): void {
    if (this.mfaForm.invalid) return;
    this.authApi.mfaEnable(this.mfaForm.controls.code.value).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('CUSTOMER.MFA_ENABLED_OK'));
        this.mfaSetup = null;
      },
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid || this.changingPassword) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const v = this.passwordForm.getRawValue();
    if (v.newPassword !== v.confirmPassword) {
      this.toast.error(this.i18n.instant('AUTH.PWD_MISMATCH'));
      return;
    }
    this.changingPassword = true;
    this.authApi
      .changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword })
      .subscribe({
        next: () => {
          this.changingPassword = false;
          this.passwordForm.reset();
          this.toast.success(this.i18n.instant('CUSTOMER.CHANGE_PWD_OK'));
          // Backend revokes all refresh sessions (including current) → re-login.
          this.store.dispatch(AuthActions.logout());
        },
        error: (err) => {
          this.changingPassword = false;
          this.toast.error(
            err?.error?.error?.message ||
              err?.message ||
              this.i18n.instant('CUSTOMER.CHANGE_PWD_FAIL'),
          );
        },
      });
  }

  revokeSession(session: AuthSession): void {
    if (session.current || this.revokingId) return;
    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_CONFIRM_TITLE'),
      message: this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_CONFIRM_MSG'),
      confirmLabel: this.i18n.instant('CUSTOMER.SESSIONS_REVOKE'),
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.revokingId = session.id;
          return this.authApi.revokeSession(session.id);
        }),
      )
      .subscribe({
        next: () => {
          this.revokingId = null;
          this.toast.success(this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_OK'));
          this.loadSessions();
        },
        error: () => {
          this.revokingId = null;
        },
      });
  }

  revokeOthers(): void {
    if (this.revokingOthers) return;
    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_OTHERS_CONFIRM_TITLE'),
      message: this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_OTHERS_CONFIRM_MSG'),
      confirmLabel: this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_OTHERS'),
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.revokingOthers = true;
          return this.authApi.revokeOtherSessions();
        }),
      )
      .subscribe({
        next: (res) => {
          this.revokingOthers = false;
          this.toast.success(
            this.i18n.instant('CUSTOMER.SESSIONS_REVOKE_OTHERS_OK', {
              count: res?.revoked ?? 0,
            }),
          );
          this.loadSessions();
        },
        error: () => {
          this.revokingOthers = false;
        },
      });
  }

  agentLabel(ua: string | null | undefined): string {
    if (!ua) return this.i18n.instant('CUSTOMER.SESSIONS_UNKNOWN_AGENT');
    const s = ua.toLowerCase();
    if (s.includes('edg/')) return 'Edge';
    if (s.includes('chrome/') && !s.includes('edg/')) return 'Chrome';
    if (s.includes('firefox/')) return 'Firefox';
    if (s.includes('safari/') && !s.includes('chrome/')) return 'Safari';
    return ua.length > 64 ? ua.slice(0, 64) + '…' : ua;
  }
}
