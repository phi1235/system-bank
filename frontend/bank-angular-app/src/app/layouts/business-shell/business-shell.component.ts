import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { BankApiService } from '../../core/services/bank-api.service';
import { BusinessContextService } from '../../core/services/business-context.service';
import { ToastService } from '../../core/services/toast.service';
import { LangSwitcherComponent } from '../../shared/components/lang-switcher/lang-switcher.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import { AuthActions } from '../../store/auth/auth.actions';
import { selectUsername } from '../../store/auth/auth.selectors';

@Component({
  selector: 'app-business-shell',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatDialogModule,
    TranslateModule,
    LangSwitcherComponent,
    NotificationBellComponent,
  ],
  templateUrl: './business-shell.component.html',
  styleUrl: './business-shell.component.scss',
})
export class BusinessShellComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly api = inject(BankApiService);
  readonly businessContext = inject(BusinessContextService);

  private readonly destroy$ = new Subject<void>();

  username$ = this.store.select(selectUsername);
  selectedOrg$ = this.businessContext.selectedOrg$;
  membership$ = this.businessContext.membership$;

  showCreateOrgModal = false;
  newOrgCode = '';
  newOrgName = '';
  newOrgTaxCode = '';
  creatingOrg = false;
  isSidebarCollapsed = false;

  toggleSidebar(): void {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }


  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.businessContext.loadOrganizations().pipe(takeUntil(this.destroy$)).subscribe();
    this.businessContext.membership$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onOrgChange(orgId: string): void {
    const org = this.businessContext.organizations().find((o) => o.id === orgId);
    if (org) {
      this.businessContext.selectOrganization(org).subscribe();
    }
  }

  openCreateOrgModal(): void {
    this.newOrgCode = '';
    this.newOrgName = '';
    this.newOrgTaxCode = '';
    this.showCreateOrgModal = true;
  }

  closeCreateOrgModal(): void {
    this.showCreateOrgModal = false;
  }

  submitCreateOrg(): void {
    if (!this.newOrgCode.trim() || !this.newOrgName.trim()) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }
    const sanitizedCode = this.newOrgCode.trim().toUpperCase().replace(/\s+/g, '_');
    this.creatingOrg = true;
    this.api
      .registerOrganization({
        code: sanitizedCode,
        name: this.newOrgName.trim(),
        taxCode: this.newOrgTaxCode.trim() || undefined,
      })
      .subscribe({
        next: (org) => {
          this.creatingOrg = false;
          this.showCreateOrgModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.businessContext.loadOrganizations().subscribe(() => {
            this.businessContext.selectOrganization(org).subscribe();
          });
        },
        error: (err) => {
          this.creatingOrg = false;
          const msg =
            err?.error?.error?.message ||
            err?.error?.message ||
            (err?.error?.errors ? Object.values(err.error.errors).join(', ') : null) ||
            this.i18n.instant('TOAST.ERROR');
          this.toast.error(msg);
        },
      });
  }

  hasPermission(perm: string): boolean {
    return this.businessContext.hasPermission(perm);
  }

  isGroupVisible(group: string): boolean {
    switch (group) {
      case 'overview':
        return true;
      case 'collection':
        return this.hasPermission('va:view') || this.hasPermission('va:create') || this.hasPermission('va:manage') ||
               this.hasPermission('collection:view') || this.hasPermission('collection:create');
      case 'payout':
        return this.hasPermission('split:view') || this.hasPermission('split:create') || this.hasPermission('split:manage') ||
               this.hasPermission('transfer:view') || this.hasPermission('transfer:create') || this.hasPermission('transfer:approve') ||
               this.hasPermission('batch:create') || this.hasPermission('batch:approve');
      case 'integration':
        return this.hasPermission('developer:view') || this.hasPermission('developer:create') || this.hasPermission('developer:manage') ||
               this.hasPermission('openbanking:view') || this.hasPermission('openbanking:create') || this.hasPermission('openbanking:manage');
      case 'organization':
        return this.hasPermission('org:members') || this.hasPermission('org:members:view') ||
               this.hasPermission('org:roles') || this.hasPermission('org:roles:view');
      default:
        return true;
    }
  }

  logout(): void {
    this.businessContext.clear();
    this.store.dispatch(AuthActions.logout());
  }
}
