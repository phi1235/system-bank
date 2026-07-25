import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { HttpErrorResponse } from '@angular/common/http';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService, RbacRole } from '../../../core/services/bank-api.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  RBAC_ACTION_COLUMNS,
  RbacActionKey,
  RbacFeatureDef,
  RbacScreenDef,
  screensByPortal,
} from '../../../core/services/rbac-features';
import { PERMISSIONS, hasAnyPermission } from '../../../core/services/rbac.util';
import { selectPermissions, selectRoles } from '../../../store/auth/auth.selectors';
import { ToastService } from '../../../core/services/toast.service';
import { combineLatest, map } from 'rxjs';

/**
 * Admin RBAC = role permission configuration only.
 * Assigning roles to users lives on /admin/users (not duplicated here).
 */
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
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatTableModule,
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

  readonly ibScreens = screensByPortal('ib');
  readonly boScreens = screensByPortal('bo');
  readonly actionCols = RBAC_ACTION_COLUMNS;

  canManageRoles$ = combineLatest([
    this.store.select(selectPermissions),
    this.store.select(selectRoles),
  ]).pipe(map(([p, r]) => hasAnyPermission(p, [PERMISSIONS.RBAC_ROLES_MANAGE], r)));

  roleCols = ['name', 'code', 'type', 'perms', 'actions'];
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
  expandedScreen: string | null = null;
  /** Collapse portals to save space: open only the relevant one by default. */
  openPortalIb = false;
  openPortalBo = true;

  ngOnInit(): void {
    this.loadRoles();
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

  selectRole(role: RbacRole, clearCreate = true): void {
      if (clearCreate) this.creating = false;
      this.selectedRole = role;
      this.draftPerms = new Set(role.permissions || []);
      this.draftRoleName = role.name;
      this.draftRoleDesc = role.description || '';
      this.draftRoleStaff = role.staff;
      this.expandedScreen = null;
      // Staff roles → BO open; customer roles → IB open (save vertical space).
      this.openPortalBo = !!role.staff;
      this.openPortalIb = !role.staff;
    }

  closeRoleDetail(): void {
      this.selectedRole = null;
      this.creating = false;
      this.draftPerms = new Set();
      this.savingRole = false;
      this.expandedScreen = null;
    }

  startCreate(): void {
      this.creating = true;
      this.selectedRole = null;
      this.newCode = '';
      this.newName = '';
      this.newDesc = '';
      this.newStaff = true;
      this.draftPerms = new Set([PERMISSIONS.IB_HOME_VIEW]);
      this.expandedScreen = null;
      // Default new role is staff → open BO matrix first.
      this.openPortalBo = true;
      this.openPortalIb = false;
    }

  togglePortalPanel(portal: 'ib' | 'bo'): void {
      if (portal === 'ib') {
        this.openPortalIb = !this.openPortalIb;
      } else {
        this.openPortalBo = !this.openPortalBo;
      }
    }

  isPortalOpen(portal: 'ib' | 'bo'): boolean {
      return portal === 'ib' ? this.openPortalIb : this.openPortalBo;
    }

  portalGrantedCount(screens: RbacScreenDef[]): number {
      return screens.reduce((sum, s) => sum + this.screenPermCount(s), 0);
    }

  portalTotalCount(screens: RbacScreenDef[]): number {
      return screens.reduce((sum, s) => sum + this.screenTotalPerms(s), 0);
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

    /** All permission codes defined on a screen. */
    screenAllPerms(screen: RbacScreenDef): string[] {
      const out: string[] = [];
      for (const f of screen.features) {
        for (const p of Object.values(f.actions)) {
          if (p) out.push(p);
        }
      }
      return out;
    }

    screenTotalPerms(screen: RbacScreenDef): number {
      return this.screenAllPerms(screen).length;
    }

    screenAllChecked(screen: RbacScreenDef): boolean {
      const perms = this.screenAllPerms(screen);
      return perms.length > 0 && perms.every((p) => this.draftPerms.has(p));
    }

    screenSomeChecked(screen: RbacScreenDef): boolean {
      const perms = this.screenAllPerms(screen);
      const n = perms.filter((p) => this.draftPerms.has(p)).length;
      return n > 0 && n < perms.length;
    }

    /** One-click: grant or revoke every permission on the screen. */
    toggleScreenAll(screen: RbacScreenDef, checked: boolean): void {
      const next = new Set(this.draftPerms);
      for (const p of this.screenAllPerms(screen)) {
        if (checked) next.add(p);
        else next.delete(p);
      }
      this.draftPerms = next;
    }

    togglePortalAll(screens: RbacScreenDef[], checked: boolean): void {
      const next = new Set(this.draftPerms);
      for (const screen of screens) {
        for (const p of this.screenAllPerms(screen)) {
          if (checked) next.add(p);
          else next.delete(p);
        }
      }
      this.draftPerms = next;
    }

    portalAllChecked(screens: RbacScreenDef[]): boolean {
      const perms = screens.flatMap((s) => this.screenAllPerms(s));
      return perms.length > 0 && perms.every((p) => this.draftPerms.has(p));
    }

    portalSomeChecked(screens: RbacScreenDef[]): boolean {
      const perms = screens.flatMap((s) => this.screenAllPerms(s));
      const n = perms.filter((p) => this.draftPerms.has(p)).length;
      return n > 0 && n < perms.length;
    }

    toggleScreenExpand(screenId: string): void {
      this.expandedScreen = this.expandedScreen === screenId ? null : screenId;
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

  onScreenPanelClosed(screenId: string): void {
    if (this.expandedScreen === screenId) {
      this.expandedScreen = null;
    }
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
}
