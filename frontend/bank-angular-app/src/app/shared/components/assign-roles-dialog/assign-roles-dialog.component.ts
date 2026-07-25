import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  BankApiService,
  RbacRole,
  RbacStaffUser,
} from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';

export interface AssignRolesDialogData {
  user: RbacStaffUser;
}

@Component({
  selector: 'app-assign-roles-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './assign-roles-dialog.component.html',
  styleUrl: './assign-roles-dialog.component.scss',
})
export class AssignRolesDialogComponent implements OnInit {
  private readonly data = inject<AssignRolesDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AssignRolesDialogComponent, RbacStaffUser | null>);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  readonly user = this.data.user;
  roles: RbacRole[] = [];
  draftRoles: string[] = [...(this.user.roles || [])];
  loading = true;
  saving = false;

  ngOnInit(): void {
    this.api.rbacRoles(false).subscribe({
      next: (roles) => {
        this.roles = roles;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toast.error(this.errorMessage(err));
      },
    });
  }

  get staffRoles(): RbacRole[] {
    return this.roles.filter((r) => r.staff);
  }

  get customerRoles(): RbacRole[] {
    return this.roles.filter((r) => !r.staff);
  }

  isChecked(code: string): boolean {
    return this.draftRoles.includes(code);
  }

  toggle(code: string, checked: boolean): void {
    if (checked) {
      if (!this.draftRoles.includes(code)) {
        this.draftRoles = [...this.draftRoles, code];
      }
    } else {
      this.draftRoles = this.draftRoles.filter((r) => r !== code);
    }
  }

  save(): void {
    if (!this.draftRoles.length) {
      this.toast.error(this.i18n.instant('ADMIN.RBAC_NEED_ROLE'));
      return;
    }
    this.saving = true;
    this.api.assignRoles(this.user.userId, this.draftRoles).subscribe({
      next: (updated) => {
        this.saving = false;
        this.toast.success(
          this.i18n.instant('ADMIN.RBAC_ASSIGN_OK', { user: updated.username }),
        );
        this.dialogRef.close(updated);
      },
      error: (err) => {
        this.saving = false;
        this.toast.error(this.errorMessage(err, 'ADMIN.RBAC_ASSIGN_FAIL'));
      },
    });
  }

  close(): void {
    this.dialogRef.close(null);
  }

  private errorMessage(err: unknown, fallbackKey = 'ADMIN.RBAC_LOAD_FAIL'): string {
    const e = err as HttpErrorResponse;
    if (
      e?.status === 403 ||
      e?.error?.error?.code === 'FORBIDDEN' ||
      String(e?.error?.error?.message || '').includes('Missing permission')
    ) {
      return this.i18n.instant('ADMIN.RBAC_NEED_RELOGIN');
    }
    if (e instanceof HttpErrorResponse) {
      return resolveHttpErrorMessage(e, this.i18n);
    }
    return this.i18n.instant(fallbackKey);
  }
}
