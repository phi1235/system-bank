import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { map } from 'rxjs';
import { PERMISSIONS } from '../../core/services/rbac.util';
import { AuthActions } from '../../store/auth/auth.actions';
import {
  selectHasAnyPermission,
  selectHasPermission,
  selectPermissions,
  selectRoles,
  selectUsername,
} from '../../store/auth/auth.selectors';
import { LangSwitcherComponent } from '../../shared/components/lang-switcher/lang-switcher.component';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatListModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss',
})
export class AdminShellComponent {
  private readonly store = inject(Store);
  username$ = this.store.select(selectUsername);
  roles$ = this.store.select(selectRoles);
  permissions$ = this.store.select(selectPermissions);

  canDashboard$ = this.store.select(selectHasPermission(PERMISSIONS.DASHBOARD_VIEW));
  canCustomers$ = this.store.select(selectHasPermission(PERMISSIONS.CUSTOMERS_LIST_VIEW));
  canAccounts$ = this.store.select(selectHasPermission(PERMISSIONS.ACCOUNTS_LOOKUP_VIEW));
  canTx$ = this.store.select(selectHasPermission(PERMISSIONS.TX_LIST_VIEW));
  canAudit$ = this.store.select(selectHasPermission(PERMISSIONS.AUDIT_LIST_VIEW));
  canRbac$ = this.store.select(selectHasPermission(PERMISSIONS.RBAC_ACCESS));
  canRisk$ = this.store.select(selectHasPermission(PERMISSIONS.RISK_VIEW));
  canUsers$ = this.store.select(
    selectHasAnyPermission([
      PERMISSIONS.USERS_PASSWORD_RESET,
      PERMISSIONS.USERS_LOCK_EXECUTE,
      PERMISSIONS.RBAC_USERS_ASSIGN,
      PERMISSIONS.RBAC_ACCESS,
    ]),
  );

  roleBadge$ = this.roles$.pipe(
    map((roles) => {
      const staff = (roles || []).filter((r) => r !== 'CUSTOMER');
      return staff[0] || roles[0] || 'STAFF';
    }),
  );

  logout(): void {
    this.store.dispatch(AuthActions.logout());
  }
}
