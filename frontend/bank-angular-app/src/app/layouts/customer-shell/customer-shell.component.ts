import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, Subscription, filter, take, takeUntil } from 'rxjs';
import { BankApiService } from '../../core/services/bank-api.service';
import { NotificationStreamService } from '../../core/services/notification-stream.service';
import { PERMISSIONS } from '../../core/services/rbac.util';
import { ToastService } from '../../core/services/toast.service';
import { LangSwitcherComponent } from '../../shared/components/lang-switcher/lang-switcher.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import { AuthActions } from '../../store/auth/auth.actions';
import { selectHasPermission, selectUsername } from '../../store/auth/auth.selectors';

@Component({
  selector: 'app-customer-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    TranslateModule,
    LangSwitcherComponent,
    NotificationBellComponent,
  ],
  templateUrl: './customer-shell.component.html',
  styleUrl: './customer-shell.component.scss',
})
export class CustomerShellComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly stream = inject(NotificationStreamService);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();
  private liveSub?: Subscription;

  username$ = this.store.select(selectUsername);

  canHome$ = this.store.select(selectHasPermission(PERMISSIONS.IB_HOME_VIEW));
  canAccounts$ = this.store.select(selectHasPermission(PERMISSIONS.IB_ACCOUNTS_VIEW));
  canTransfer$ = this.store.select(selectHasPermission(PERMISSIONS.IB_TRANSFER_VIEW));
  canCards$ = this.store.select(selectHasPermission(PERMISSIONS.IB_CARDS_VIEW));
  canWealth$ = this.store.select(selectHasPermission(PERMISSIONS.IB_WEALTH_VIEW));
  canSupport$ = this.store.select(selectHasPermission(PERMISSIONS.IB_SUPPORT_VIEW));
  canProfile$ = this.store.select(selectHasPermission(PERMISSIONS.IB_PROFILE_VIEW));
  canHistory$ = this.store.select(selectHasPermission(PERMISSIONS.IB_HISTORY_VIEW));
  canNotifications$ = this.store.select(selectHasPermission(PERMISSIONS.IB_NOTIFICATIONS_VIEW));

  ngOnInit(): void {
    this.canNotifications$
      .pipe(
        filter((ok) => !!ok),
        take(1),
        takeUntil(this.destroy$),
      )
      .subscribe(() => {
        this.api
          .notificationUnreadCount()
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: (r) => this.stream.setUnreadCount(r?.unread ?? 0),
            error: () => this.stream.setUnreadCount(0),
          });
        this.stream.connect();
        this.liveSub = this.stream.liveEvents$
          .pipe(takeUntil(this.destroy$))
          .subscribe((item) => {
            const preview = (item.body || item.template || '').slice(0, 80);
            this.toast.info(this.i18n.instant('CUSTOMER.NOTIF_LIVE', { text: preview }));
          });
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.liveSub?.unsubscribe();
    this.stream.disconnect();
  }

  logout(): void {
    this.stream.disconnect();
    this.store.dispatch(AuthActions.logout());
  }
}
