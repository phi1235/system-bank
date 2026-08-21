import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { BusinessMember } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-members',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
  ],
  templateUrl: './business-members.component.html',
  styleUrl: './business-members.component.scss',
})
export class BusinessMembersComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  displayedColumns: string[] = ['userId', 'businessRole', 'status', 'createdAt', 'actions'];
  members: BusinessMember[] = [];
  customRoles: { code: string; displayName: string }[] = [];
  loading = false;

  // Add Member Modal
  showAddModal = false;
  newUserId = '';
  newRole = 'BUSINESS_OPERATOR';
  addingMember = false;

  ngOnInit(): void {
    this.businessContext.selectedOrg$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadMembers();
      this.loadCustomRoles();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadCustomRoles(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.listCustomRoles(orgId).subscribe({
      next: (roles) => {
        if (roles && roles.length > 0) {
          this.customRoles = roles.map((r) => ({ code: r.code, displayName: r.displayName }));
        } else {
          this.customRoles = [
            { code: 'BUSINESS_OWNER', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_OWNER') },
            { code: 'BUSINESS_FINANCE', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_FINANCE') },
            { code: 'BUSINESS_OPERATOR', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_OPERATOR') },
            { code: 'BUSINESS_VIEWER', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_VIEWER') },
          ];
        }
      },
      error: () => {
        this.customRoles = [
          { code: 'BUSINESS_OWNER', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_OWNER') },
          { code: 'BUSINESS_FINANCE', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_FINANCE') },
          { code: 'BUSINESS_OPERATOR', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_OPERATOR') },
          { code: 'BUSINESS_VIEWER', displayName: this.i18n.instant('BUSINESS.MEMBERS.ROLE_VIEWER') },
        ];
      },
    });
  }

  loadMembers(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.loading = true;
    this.api.listBusinessMembers(orgId).subscribe({
      next: (res) => {
        this.members = res || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  openAddModal(): void {
    this.newUserId = '';
    this.newRole = this.customRoles.length > 0 ? this.customRoles[0].code : 'BUSINESS_OPERATOR';
    this.showAddModal = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
  }


  submitAddMember(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!this.newUserId.trim()) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }

    this.addingMember = true;
    this.api
      .addBusinessMember(orgId, {
        userIdentifier: this.newUserId.trim(),
        role: this.newRole,
      })
      .subscribe({
        next: () => {
          this.addingMember = false;
          this.showAddModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.loadMembers();
        },
        error: (err) => {
          this.addingMember = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  removeMember(member: BusinessMember): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.removeBusinessMember(orgId, member.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadMembers();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }
}
