import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription, filter, map, take } from 'rxjs';
import { BankApiService } from '../../core/services/bank-api.service';
import { OpsNotificationStreamService } from '../../core/services/ops-notification-stream.service';
import { PERMISSIONS } from '../../core/services/rbac.util';
import { ToastService } from '../../core/services/toast.service';
import { LangSwitcherComponent } from '../../shared/components/lang-switcher/lang-switcher.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import { AuthActions } from '../../store/auth/auth.actions';
import {
  selectHasAnyPermission,
  selectHasPermission,
  selectPermissions,
  selectRoles,
  selectUsername,
} from '../../store/auth/auth.selectors';

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
    NotificationBellComponent,
  ],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss',
})
export class AdminShellComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly stream = inject(OpsNotificationStreamService);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private liveSub?: Subscription;
  private permSub?: Subscription;

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
  canOpsNotifications$ = this.store.select(
    selectHasPermission(PERMISSIONS.NOTIFICATIONS_OPS_VIEW),
  );
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

  ngOnInit(): void {
    this.permSub = this.canOpsNotifications$
      .pipe(
        filter((ok) => !!ok),
        take(1),
      )
      .subscribe(() => {
        this.api.adminOpsNotificationUnreadCount().subscribe({
          next: (r) => this.stream.setUnreadCount(r?.unread ?? 0),
          error: () => this.stream.setUnreadCount(0),
        });
        this.stream.connect();
        this.liveSub = this.stream.liveEvents$.subscribe((item) => {
          const preview = (item.body || item.template || '').slice(0, 80);
          this.toast.info(this.i18n.instant('ADMIN.NOTIF_LIVE', { text: preview }));
        });
      });
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
    this.permSub?.unsubscribe();
    this.stream.disconnect();
  }

  logout(): void {
    this.stream.disconnect();
    this.store.dispatch(AuthActions.logout());
  }
}
