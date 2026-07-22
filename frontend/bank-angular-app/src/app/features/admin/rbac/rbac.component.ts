import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { HttpErrorResponse } from '@angular/common/http';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService, RbacRole, RbacStaffUser } from '../../../core/services/bank-api.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  RBAC_ACTION_COLUMNS,
  RBAC_SCREENS,
  RbacActionKey,
  RbacFeatureDef,
  RbacScreenDef,
  screensByPortal,
} from '../../../core/services/rbac-features';
import { PERMISSIONS, hasAnyPermission } from '../../../core/services/rbac.util';
import { selectPermissions, selectRoles } from '../../../store/auth/auth.selectors';
import { ToastService } from '../../../core/services/toast.service';
import { combineLatest, map } from 'rxjs';

@Component({
  selector: 'app-admin-rbac',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatChipsModule,
    MatDividerModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatListModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
    MatSlideToggleModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './rbac.component.html',
  styleUrl: './rbac.component.scss',
})
export class AdminRbacComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);

  readonly screens = RBAC_SCREENS;
  readonly ibScreens = screensByPortal('ib');
  readonly boScreens = screensByPortal('bo');
  readonly actionCols = RBAC_ACTION_COLUMNS;
  readonly featureTableCols = ['feature', ...RBAC_ACTION_COLUMNS.map((c) => c.key), 'hint'];

  tabIndex = 0;

  canAssignUsers$ = combineLatest([
    this.store.select(selectPermissions),
    this.store.select(selectRoles),
  ]).pipe(map(([p, r]) => hasAnyPermission(p, [PERMISSIONS.RBAC_USERS_ASSIGN], r)));

  canManageRoles$ = combineLatest([
    this.store.select(selectPermissions),
    this.store.select(selectRoles),
  ]).pipe(map(([p, r]) => hasAnyPermission(p, [PERMISSIONS.RBAC_ROLES_MANAGE], r)));

  // Users
  userCols = ['username', 'email', 'roles', 'type', 'actions'];
  users: RbacStaffUser[] = [];
  q = '';
  page = 0;
  size = 10;
  total = 0;
  usersLoading = false;
  selectedUser: RbacStaffUser | null = null;
  draftUserRoles: string[] = [];
  savingUser = false;

  // Roles
  roles: RbacRole[] = [];
  rolesLoading = false;
  roleFilter = '';
  selectedRole: RbacRole | null = null;
  draftPerms = new Set<string>();
  draftRoleName = '';
  draftRoleDesc = '';
  draftRoleStaff = true;
  savingRole = false;
  creating = false;
  newCode = '';
  newName = '';
  newDesc = '';
  newStaff = true;
  expandedScreen: string | null = 'customers';

  ngOnInit(): void {
    this.loadRoles();
    this.searchUsers();
  }

  get filteredRoles(): RbacRole[] {
    const f = this.roleFilter.trim().toLowerCase();
    if (!f) return this.roles;
    return this.roles.filter(
      (r) =>
        r.code.toLowerCase().includes(f) ||
        (r.name || '').toLowerCase().includes(f) ||
        (r.description || '').toLowerCase().includes(f),
    );
  }

  loadRoles(): void {
    this.rolesLoading = true;
    this.api.rbacRoles(false).subscribe({
      next: (roles) => {
        this.roles = roles;
        this.rolesLoading = false;
        if (this.selectedRole) {
          const fresh = roles.find((r) => r.code === this.selectedRole!.code);
          if (fresh) this.selectRole(fresh, false);
        }
      },
      error: (err) => {
        this.rolesLoading = false;
        this.toast.error(this.rbacErrorMessage(err));
      },
    });
  }

  // ── Users ──
  searchUsers(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.usersLoading = true;
    this.closeUserDrawer();
    this.api.rbacUsers(this.page, this.size, this.q?.trim() || undefined).subscribe({
      next: (p) => {
        this.users = p.items || [];
        this.total = p.totalElements ?? this.users.length;
        this.page = p.page ?? this.page;
        this.usersLoading = false;
      },
      error: (err) => {
        this.usersLoading = false;
        this.users = [];
        this.total = 0;
        this.toast.error(this.rbacErrorMessage(err));
      },
    });
  }

  private rbacErrorMessage(err: unknown, fallbackKey = 'ADMIN.RBAC_LOAD_FAIL'): string {
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

  onPage(e: PageEvent): void {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.searchUsers(false);
  }

  openUserAssign(user: RbacStaffUser): void {
    this.selectedUser = user;
    this.draftUserRoles = [...(user.roles || [])];
  }

  closeUserDrawer(): void {
    this.selectedUser = null;
    this.draftUserRoles = [];
    this.savingUser = false;
  }

  toggleUserRole(code: string, checked: boolean): void {
    if (checked) {
      if (!this.draftUserRoles.includes(code)) this.draftUserRoles = [...this.draftUserRoles, code];
    } else {
      this.draftUserRoles = this.draftUserRoles.filter((r) => r !== code);
    }
  }

  isUserRoleChecked(code: string): boolean {
    return this.draftUserRoles.includes(code);
  }

  get userEffectiveSummary(): { screen: string; items: string[] }[] {
    const perms = new Set<string>();
    for (const code of this.draftUserRoles) {
      const role = this.roles.find((r) => r.code === code);
      (role?.permissions || []).forEach((p) => perms.add(p));
    }
    return this.screens
      .map((s) => {
        const items: string[] = [];
        for (const f of s.features) {
          const granted = Object.entries(f.actions)
            .filter(([, perm]) => perm && perms.has(perm))
            .map(([act]) => this.actionLabel(act as RbacActionKey));
          if (granted.length) {
            items.push(`${this.featLabel(f)} (${granted.join(', ')})`);
          }
        }
        return { screen: this.screenLabel(s), items };
      })
      .filter((x) => x.items.length > 0);
  }

  saveUserRoles(): void {
    if (!this.selectedUser) return;
    if (!this.draftUserRoles.length) {
      this.toast.error(this.i18n.instant('ADMIN.RBAC_NEED_ROLE'));
      return;
    }
    this.savingUser = true;
    this.api.assignRoles(this.selectedUser.userId, this.draftUserRoles).subscribe({
      next: (updated) => {
        this.savingUser = false;
        this.toast.success(this.i18n.instant('ADMIN.RBAC_ASSIGN_OK', { user: updated.username }));
        const idx = this.users.findIndex((u) => u.userId === updated.userId);
        if (idx >= 0) this.users[idx] = updated;
        this.selectedUser = updated;
        this.draftUserRoles = [...updated.roles];
      },
      error: (err) => {
        this.savingUser = false;
        this.toast.error(this.rbacErrorMessage(err, 'ADMIN.RBAC_ASSIGN_FAIL'));
      },
    });
  }

  // ── Roles ──
  selectRole(role: RbacRole, clearCreate = true): void {
    if (clearCreate) this.creating = false;
    this.selectedRole = role;
    this.draftPerms = new Set(role.permissions || []);
    this.draftRoleName = role.name;
    this.draftRoleDesc = role.description || '';
    this.draftRoleStaff = role.staff;
  }

  startCreate(): void {
    this.creating = true;
    this.selectedRole = null;
    this.newCode = '';
    this.newName = '';
    this.newDesc = '';
    this.newStaff = true;
    this.draftPerms = new Set([PERMISSIONS.IB_HOME_VIEW]);
    this.expandedScreen = 'ib-home';
  }

  cancelCreate(): void {
    this.creating = false;
    this.draftPerms = new Set();
  }

  hasPerm(code: string | undefined | null): boolean {
    return !!code && this.draftPerms.has(code);
  }

  setPerm(code: string | undefined | null, checked: boolean): void {
    if (!code) return;
    const next = new Set(this.draftPerms);
    if (checked) next.add(code);
    else next.delete(code);
    this.draftPerms = next;
  }

  onActionToggle(perm: string | undefined, e: MatCheckboxChange): void {
    this.setPerm(perm, e.checked);
  }

  featureHasAction(f: RbacFeatureDef, key: RbacActionKey): boolean {
    return !!f.actions[key];
  }

  featureActionPerm(f: RbacFeatureDef, key: RbacActionKey): string | undefined {
    return f.actions[key];
  }

  /** Toggle all actions on a feature */
  toggleFeatureAll(f: RbacFeatureDef, checked: boolean): void {
    Object.values(f.actions).forEach((p) => this.setPerm(p, checked));
  }

  featureAllChecked(f: RbacFeatureDef): boolean {
    const perms = Object.values(f.actions).filter(Boolean) as string[];
    return perms.length > 0 && perms.every((p) => this.draftPerms.has(p));
  }

  featureSomeChecked(f: RbacFeatureDef): boolean {
    const perms = Object.values(f.actions).filter(Boolean) as string[];
    const n = perms.filter((p) => this.draftPerms.has(p)).length;
    return n > 0 && n < perms.length;
  }

  screenPermCount(screen: RbacScreenDef): number {
    let n = 0;
    for (const f of screen.features) {
      Object.values(f.actions).forEach((p) => {
        if (p && this.draftPerms.has(p)) n++;
      });
    }
    return n;
  }

  screenLabel(s: RbacScreenDef): string {
    const t = this.i18n.instant(s.labelKey);
    return t !== s.labelKey ? t : s.label;
  }

  featLabel(f: RbacFeatureDef): string {
    const t = this.i18n.instant(f.labelKey);
    return t !== f.labelKey ? t : f.label;
  }

  actionLabel(key: RbacActionKey): string {
    const col = this.actionCols.find((c) => c.key === key);
    if (!col) return key;
    const t = this.i18n.instant(col.labelKey);
    return t !== col.labelKey ? t : col.label;
  }

  rolePermCount(role: RbacRole): number {
    return role.permissions?.length || 0;
  }

  saveRoleMatrix(): void {
    const perms = [...this.draftPerms];
    if (this.creating) {
      const code = this.newCode.trim().toUpperCase();
      if (!code || !this.newName.trim()) {
        this.toast.error(this.i18n.instant('ADMIN.RBAC_ROLE_NEED_FIELDS'));
        return;
      }
      this.savingRole = true;
      this.api
        .createRole({
          code,
          name: this.newName.trim(),
          description: this.newDesc.trim(),
          staff: this.newStaff,
          permissions: perms,
        })
        .subscribe({
          next: (created) => {
            this.savingRole = false;
            this.creating = false;
            this.toast.success(this.i18n.instant('ADMIN.RBAC_ROLE_CREATE_OK', { role: created.code }));
            this.patchRoleList(created);
            this.selectRole(created);
          },
          error: (err) => {
            this.savingRole = false;
            this.toast.error(this.rbacErrorMessage(err, 'ADMIN.RBAC_ROLE_CREATE_FAIL'));
          },
        });
      return;
    }

    if (!this.selectedRole) return;
    this.savingRole = true;
    const code = this.selectedRole.code;
    this.api
      .updateRole(code, {
        name: this.draftRoleName,
        description: this.draftRoleDesc,
        staff: this.draftRoleStaff,
      })
      .subscribe({
        next: () => {
          this.api.updateRolePermissions(code, perms).subscribe({
            next: (updated) => {
              this.savingRole = false;
              this.toast.success(this.i18n.instant('ADMIN.RBAC_ROLE_SAVE_OK', { role: updated.code }));
              this.patchRoleList(updated);
              this.selectRole(updated, false);
            },
            error: (err) => {
              this.savingRole = false;
              this.toast.error(this.rbacErrorMessage(err, 'ADMIN.RBAC_ROLE_SAVE_FAIL'));
            },
          });
        },
        error: (err) => {
          this.savingRole = false;
          this.toast.error(this.rbacErrorMessage(err, 'ADMIN.RBAC_ROLE_SAVE_FAIL'));
        },
      });
  }

  patchRoleList(updated: RbacRole): void {
    const idx = this.roles.findIndex((r) => r.code === updated.code);
    if (idx >= 0) this.roles[idx] = updated;
    else this.roles = [...this.roles, updated].sort((a, b) => a.code.localeCompare(b.code));
  }

  onTabChange(index: number): void {
    this.tabIndex = index;
    if (index === 1) this.loadRoles();
  }
}
