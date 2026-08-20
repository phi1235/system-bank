import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
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

  ngOnInit(): void {
    this.businessContext.loadOrganizations().pipe(takeUntil(this.destroy$)).subscribe();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onOrgChange(orgId: string): void {
    const org = this.businessContext.organizations().find((o) => o.id === orgId);
    if (org) {
      this.businessContext.selectOrganization(org);
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
    this.creatingOrg = true;
    this.api
      .registerOrganization({
        code: this.newOrgCode.trim().toUpperCase(),
        name: this.newOrgName.trim(),
        taxCode: this.newOrgTaxCode.trim() || undefined,
      })
      .subscribe({
        next: (org) => {
          this.creatingOrg = false;
          this.showCreateOrgModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.businessContext.loadOrganizations().subscribe(() => {
            this.businessContext.selectOrganization(org);
          });
        },
        error: (err) => {
          this.creatingOrg = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  logout(): void {
    this.store.dispatch(AuthActions.logout());
  }
}
