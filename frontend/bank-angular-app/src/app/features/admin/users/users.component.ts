import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { BankApiService, RbacStaffUser } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { selectHasPermission, selectUser } from '../../../store/auth/auth.selectors';
import { map } from 'rxjs';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss',
})
export class AdminUsersComponent implements OnInit {
  private readonly bankApi = inject(BankApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);

  canReset$ = this.store.select(selectHasPermission(PERMISSIONS.USERS_PASSWORD_RESET));
  canLock$ = this.store.select(selectHasPermission(PERMISSIONS.USERS_LOCK_EXECUTE));
  /** Current signed-in admin userId — cannot lock / blind-reset self */
  meUserId$ = this.store.select(selectUser).pipe(map((u) => u?.userId ?? null));
  meUserId: string | null = null;

  rows: RbacStaffUser[] = [];
  q = '';
  cols = ['username', 'email', 'roles', 'status', 'actions'];
  loading = false;
  busyId: string | null = null;

  ngOnInit(): void {
    this.meUserId$.subscribe((id) => {
      this.meUserId = id;
    });
    this.load();
  }

  isSelf(u: RbacStaffUser): boolean {
    return !!this.meUserId && u.userId === this.meUserId;
  }

  load(): void {
    this.loading = true;
    this.bankApi.rbacUsers(0, 100, this.q || undefined).subscribe({
      next: (p) => {
        this.rows = p.items || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error(this.i18n.instant('ADMIN.USERS_LOAD_FAIL'));
      },
    });
  }

  /** Direct blind reset — confirm only, no second checker. */
  resetPassword(u: RbacStaffUser): void {
    if (this.isSelf(u)) {
      this.toast.error(this.i18n.instant('ADMIN.CANNOT_ACT_SELF'));
      return;
    }
    const ok = confirm(this.i18n.instant('ADMIN.PWD_FULFILL_CONFIRM', { user: u.username }));
    if (!ok) return;
    this.busyId = u.userId;
    this.authApi.resetUserPassword(u.userId, 'EMAIL').subscribe({
      next: (res) => {
        this.busyId = null;
        this.toast.success(res.message || this.i18n.instant('ADMIN.PWD_FULFILL_OK'));
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.toast.error(
          err?.error?.error?.message || this.i18n.instant('ADMIN.PWD_FULFILL_FAIL'),
        );
      },
    });
  }

  toggleLock(u: RbacStaffUser): void {
    if (this.isSelf(u)) {
      this.toast.error(this.i18n.instant('ADMIN.CANNOT_LOCK_SELF'));
      return;
    }
    if (u.enabled) {
      const reason =
        prompt(this.i18n.instant('ADMIN.LOCK_REASON_PROMPT')) || 'Locked by admin';
      if (!confirm(this.i18n.instant('ADMIN.LOCK_CONFIRM', { user: u.username }))) return;
      this.busyId = u.userId;
      this.authApi.lockUser(u.userId, reason).subscribe({
        next: () => {
          this.busyId = null;
          this.toast.success(this.i18n.instant('ADMIN.LOCK_OK', { user: u.username }));
          this.load();
        },
        error: (err) => {
          this.busyId = null;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('ADMIN.LOCK_FAIL'));
        },
      });
    } else {
      if (!confirm(this.i18n.instant('ADMIN.UNLOCK_CONFIRM', { user: u.username }))) return;
      this.busyId = u.userId;
      this.authApi.unlockUser(u.userId).subscribe({
        next: () => {
          this.busyId = null;
          this.toast.success(this.i18n.instant('ADMIN.UNLOCK_OK', { user: u.username }));
          this.load();
        },
        error: (err) => {
          this.busyId = null;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('ADMIN.UNLOCK_FAIL'));
        },
      });
    }
  }
}
