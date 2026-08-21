import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import {
  BusinessPermissionActionDto,
  BusinessPermissionFeatureDto,
  BusinessPermissionModuleDto,
  CustomRoleItem,
  CustomRoleRequest,
} from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-roles',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatDividerModule,
    TranslateModule,
  ],
  templateUrl: './business-roles.component.html',
  styleUrl: './business-roles.component.scss',
})
export class BusinessRolesComponent implements OnInit, OnDestroy {
  private readonly bankApi = inject(BankApiService);
  readonly businessContext = inject(BusinessContextService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly destroy$ = new Subject<void>();

  actionCols: BusinessPermissionActionDto[] = [];
  modules: BusinessPermissionModuleDto[] = [];

  displayedColumns: string[] = ['code', 'displayName', 'type', 'permissions', 'description', 'createdAt', 'actions'];
  roles: CustomRoleItem[] = [];
  filteredRoles: CustomRoleItem[] = [];
  searchTerm = '';
  loading = false;
  saving = false;
  error: string | null = null;

  // Modal State
  showModal = false;
  isEditing = false;
  editingRoleId: string | null = null;
  roleForm: CustomRoleRequest = {
    code: '',
    displayName: '',
    description: '',
    permissions: [],
  };

  ngOnInit(): void {
    this.loadPermissionMatrix();
    this.businessContext.selectedOrg$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.loadRoles();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get currentOrgId(): string | null {
    return this.businessContext.getSelectedOrgId();
  }

  get canManageRoles(): boolean {
    return this.businessContext.hasPermission('org:roles') || this.businessContext.hasPermission('org:roles:manage');
  }

  loadPermissionMatrix(): void {
    this.bankApi.getBusinessPermissionMatrix().subscribe({
      next: (resp) => {
        if (resp) {
          this.actionCols = resp.actionColumns || [];
          this.modules = resp.modules || [];
        }
      },
      error: () => {
        // Fallback default structure
        this.actionCols = [
          { key: 'view', labelKey: 'B2B.RBAC.ACT_VIEW', icon: 'visibility' },
          { key: 'create', labelKey: 'B2B.RBAC.ACT_CREATE', icon: 'add_circle' },
          { key: 'manage', labelKey: 'B2B.RBAC.ACT_MANAGE', icon: 'edit' },
          { key: 'delete', labelKey: 'B2B.RBAC.ACT_DELETE', icon: 'delete' },
          { key: 'approve', labelKey: 'B2B.RBAC.ACT_APPROVE', icon: 'verified' },
        ];
      },
    });
  }

  loadRoles(): void {
    const orgId = this.currentOrgId;
    if (!orgId) {
      this.roles = [];
      this.filteredRoles = [];
      return;
    }

    this.loading = true;
    this.error = null;
    this.bankApi.listCustomRoles(orgId).subscribe({
      next: (data) => {
        this.roles = data || [];
        this.applyFilter();
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || this.translate.instant('B2B.ROLES.LOAD_ERROR');
        this.roles = [];
        this.filteredRoles = [];
        this.loading = false;
      },
    });
  }

  applyFilter(): void {
    const q = (this.searchTerm || '').trim().toLowerCase();
    if (!q) {
      this.filteredRoles = [...this.roles];
    } else {
      this.filteredRoles = this.roles.filter(
        (r) =>
          r.code.toLowerCase().includes(q) ||
          r.displayName.toLowerCase().includes(q) ||
          (r.description && r.description.toLowerCase().includes(q))
      );
    }
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.editingRoleId = null;
    this.roleForm = {
      code: '',
      displayName: '',
      description: '',
      permissions: [],
    };
    this.showModal = true;
  }

  openEditModal(role: CustomRoleItem): void {
    if (role.ownerRole) return;
    this.isEditing = true;
    this.editingRoleId = role.id;
    this.roleForm = {
      code: role.code,
      displayName: role.displayName,
      description: role.description || '',
      permissions: role.permissions ? [...role.permissions] : [],
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingRoleId = null;
  }

  // --- Dynamic RBAC Matrix Helper Methods ---

  hasPerm(perm: string | undefined): boolean {
    if (!perm) return false;
    return this.roleForm.permissions.includes(perm);
  }

  togglePerm(perm: string | undefined): void {
    if (!perm) return;
    const idx = this.roleForm.permissions.indexOf(perm);
    if (idx >= 0) {
      this.roleForm.permissions.splice(idx, 1);
    } else {
      this.roleForm.permissions.push(perm);
    }
  }

  featureHasAction(feature: BusinessPermissionFeatureDto, action: string): boolean {
    return !!feature.actions && !!feature.actions[action];
  }

  featureActionPerm(feature: BusinessPermissionFeatureDto, action: string): string | undefined {
    return feature.actions ? feature.actions[action] : undefined;
  }

  getFeaturePerms(feature: BusinessPermissionFeatureDto): string[] {
    if (!feature.actions) return [];
    return Object.values(feature.actions).filter((p): p is string => !!p);
  }

  featureAllChecked(feature: BusinessPermissionFeatureDto): boolean {
    const perms = this.getFeaturePerms(feature);
    if (perms.length === 0) return false;
    return perms.every((p) => this.hasPerm(p));
  }

  featureSomeChecked(feature: BusinessPermissionFeatureDto): boolean {
    const perms = this.getFeaturePerms(feature);
    if (perms.length === 0) return false;
    const checkedCount = perms.filter((p) => this.hasPerm(p)).length;
    return checkedCount > 0 && checkedCount < perms.length;
  }

  toggleFeatureAll(feature: BusinessPermissionFeatureDto, checked: boolean): void {
    const perms = this.getFeaturePerms(feature);
    if (checked) {
      for (const p of perms) {
        if (!this.hasPerm(p)) {
          this.roleForm.permissions.push(p);
        }
      }
    } else {
      this.roleForm.permissions = this.roleForm.permissions.filter((p) => !perms.includes(p));
    }
  }

  getModulePerms(mod: BusinessPermissionModuleDto): string[] {
    const list: string[] = [];
    if (mod.features) {
      for (const f of mod.features) {
        list.push(...this.getFeaturePerms(f));
      }
    }
    return list;
  }

  moduleAllChecked(mod: BusinessPermissionModuleDto): boolean {
    const perms = this.getModulePerms(mod);
    if (perms.length === 0) return false;
    return perms.every((p) => this.hasPerm(p));
  }

  moduleSomeChecked(mod: BusinessPermissionModuleDto): boolean {
    const perms = this.getModulePerms(mod);
    if (perms.length === 0) return false;
    const count = perms.filter((p) => this.hasPerm(p)).length;
    return count > 0 && count < perms.length;
  }

  toggleModuleAll(mod: BusinessPermissionModuleDto, checked: boolean): void {
    const perms = this.getModulePerms(mod);
    if (checked) {
      for (const p of perms) {
        if (!this.hasPerm(p)) {
          this.roleForm.permissions.push(p);
        }
      }
    } else {
      this.roleForm.permissions = this.roleForm.permissions.filter((p) => !perms.includes(p));
    }
  }

  getModuleTotalPerms(mod: BusinessPermissionModuleDto): number {
    return this.getModulePerms(mod).length;
  }

  getModuleGrantedPerms(mod: BusinessPermissionModuleDto): number {
    const perms = this.getModulePerms(mod);
    return perms.filter((p) => this.hasPerm(p)).length;
  }

  getTotalMatrixPerms(): number {
    let count = 0;
    for (const m of this.modules) {
      count += this.getModuleTotalPerms(m);
    }
    return count;
  }

  getFriendlyPermLabel(code: string | any): string {
    if (!code || typeof code !== 'string') return '';
    const formatted = code.replace(/:/g, '_').toUpperCase();
    const key = `B2B.PERMISSIONS.${formatted}`;
    const translated = this.translate.instant(key);
    return translated !== key ? translated : code;
  }

  getFriendlyTooltipPerms(permissions: string[] | undefined): string {
    if (!permissions || permissions.length === 0) return '';
    return permissions.map((p) => this.getFriendlyPermLabel(p)).join('\n• ');
  }

  saveRole(): void {
    const orgId = this.currentOrgId;
    if (!orgId || !this.roleForm.displayName.trim() || (!this.isEditing && !this.roleForm.code.trim())) {
      return;
    }

    this.saving = true;
    this.error = null;

    if (this.isEditing && this.editingRoleId) {
      this.bankApi.updateCustomRole(orgId, this.editingRoleId, this.roleForm).subscribe({
        next: () => {
          this.saving = false;
          this.closeModal();
          this.toast.success(this.translate.instant('B2B.ROLES.UPDATE_SUCCESS'));
          this.loadRoles();
        },
        error: (err) => {
          this.saving = false;
          const msg = err?.error?.message || this.translate.instant('B2B.ROLES.UPDATE_ERROR');
          this.toast.error(msg);
        },
      });
    } else {
      this.bankApi.createCustomRole(orgId, this.roleForm).subscribe({
        next: () => {
          this.saving = false;
          this.closeModal();
          this.toast.success(this.translate.instant('B2B.ROLES.CREATE_SUCCESS'));
          this.loadRoles();
        },
        error: (err) => {
          this.saving = false;
          const msg = err?.error?.message || this.translate.instant('B2B.ROLES.CREATE_ERROR');
          this.toast.error(msg);
        },
      });
    }
  }

  deleteRole(role: CustomRoleItem): void {
    const orgId = this.currentOrgId;
    if (!orgId || role.ownerRole || role.defaultRole) return;

    if (!confirm(this.translate.instant('B2B.ROLES.DELETE_CONFIRM', { name: role.displayName }))) {
      return;
    }

    this.bankApi.deleteCustomRole(orgId, role.id).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('B2B.ROLES.DELETE_SUCCESS'));
        this.loadRoles();
      },
      error: (err) => {
        const msg = err?.error?.message || this.translate.instant('B2B.ROLES.DELETE_ERROR');
        this.toast.error(msg);
      },
    });
  }
}
