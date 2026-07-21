import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, Input, OnDestroy, ViewChild, inject } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenu, MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { NotificationItem } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { NotificationStreamService } from '../../../core/services/notification-stream.service';
import { OpsNotificationStreamService } from '../../../core/services/ops-notification-stream.service';
import { ToastService } from '../../../core/services/toast.service';

export type NotificationBellMode = 'customer' | 'ops';

/**
 * Header notification bell + dropdown panel (app-style popover).
 * Loads a recent page when opened; mark-read / mark-all in place.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    CommonModule,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) mode!: NotificationBellMode;
  @ViewChild('panel') private menu?: MatMenu;

  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly customerStream = inject(NotificationStreamService);
  private readonly opsStream = inject(OpsNotificationStreamService);
  private liveSub?: Subscription;

  items: NotificationItem[] = [];
  loading = false;
  markingAll = false;
  loadedOnce = false;
  private readonly pageSize = 12;

  get unread$() {
    return this.mode === 'ops' ? this.opsStream.unreadCount$ : this.customerStream.unreadCount$;
  }

  get unread(): number {
    return this.mode === 'ops' ? this.opsStream.unreadCount : this.customerStream.unreadCount;
  }

  get titleKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_TITLE' : 'CUSTOMER.NOTIF_TITLE';
  }

  get emptyKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_EMPTY' : 'CUSTOMER.NOTIF_EMPTY';
  }

  get markAllKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_MARK_ALL' : 'CUSTOMER.NOTIF_MARK_ALL';
  }

  get markReadKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_MARK_READ' : 'CUSTOMER.NOTIF_MARK_READ';
  }

  get failKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_ACTION_FAIL' : 'CUSTOMER.NOTIF_ACTION_FAIL';
  }

  get loadFailKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_LOAD_FAIL' : 'CUSTOMER.NOTIF_LOAD_FAIL';
  }

  get markAllOkKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_MARK_ALL_OK' : 'CUSTOMER.NOTIF_MARK_ALL_OK';
  }

  ngAfterViewInit(): void {
    // Material 19: overlayPanelClass is not a template input; set on the pane host.
    if (this.menu) {
      this.menu.overlayPanelClass = 'notif-dropdown-overlay';
    }
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
  }

  /** Keep menu open when clicking inside the panel. */
  stopClose(event: MouseEvent): void {
    event.stopPropagation();
  }

  onMenuOpened(): void {
    this.bindLive();
    this.reload();
    // Material hard-caps .mat-mdc-menu-panel at 280px; force wide width after paint.
    this.forcePanelWidth();
    requestAnimationFrame(() => this.forcePanelWidth());
    setTimeout(() => this.forcePanelWidth(), 0);
    setTimeout(() => this.forcePanelWidth(), 80);
  }

  onMenuClosed(): void {
    // keep list cached; live sub stays for badge updates while shell is alive
  }

  /**
   * Force overlay + menu panel width. CSS alone can lose to Material's
   * max-width:280px depending on cascade order / HMR timing.
   */
  private forcePanelWidth(): void {
    const width = 'min(720px, calc(100vw - 1.25rem))';
    const panel =
      (document.querySelector('.mat-mdc-menu-panel.notif-dropdown-panel') as HTMLElement | null) ??
      (document.querySelector('.mat-mdc-menu-panel:has(.notif-panel)') as HTMLElement | null);
    if (!panel) {
      return;
    }
    panel.style.setProperty('width', width, 'important');
    panel.style.setProperty('min-width', width, 'important');
    panel.style.setProperty('max-width', width, 'important');

    const pane = panel.closest('.cdk-overlay-pane') as HTMLElement | null;
    if (pane) {
      pane.style.setProperty('width', width, 'important');
      pane.style.setProperty('min-width', width, 'important');
      pane.style.setProperty('max-width', width, 'important');
    }

    const content = panel.querySelector('.mat-mdc-menu-content') as HTMLElement | null;
    if (content) {
      content.style.setProperty('width', '100%', 'important');
      content.style.setProperty('max-width', 'none', 'important');
      content.style.setProperty('padding', '0', 'important');
    }

    const inner = panel.querySelector('.notif-panel') as HTMLElement | null;
    if (inner) {
      inner.style.setProperty('width', '100%', 'important');
      inner.style.setProperty('min-width', '100%', 'important');
      inner.style.setProperty('max-width', 'none', 'important');
    }
  }

  markRead(item: NotificationItem, event?: MouseEvent): void {
    event?.stopPropagation();
    if (item.read) {
      return;
    }
    const req =
      this.mode === 'ops'
        ? this.api.markAdminOpsNotificationRead(item.id)
        : this.api.markNotificationRead(item.id);
    req.subscribe({
      next: (updated) => {
        this.items = this.items.map((x) => (x.id === updated.id ? updated : x));
        this.setUnread(Math.max(0, this.unread - 1));
      },
      error: () => this.toast.error(this.i18n.instant(this.failKey)),
    });
  }

  markAll(event?: MouseEvent): void {
    event?.stopPropagation();
    if (this.markingAll || this.unread === 0) {
      return;
    }
    this.markingAll = true;
    const req =
      this.mode === 'ops'
        ? this.api.markAllAdminOpsNotificationsRead()
        : this.api.markAllNotificationsRead();
    req.subscribe({
      next: () => {
        this.markingAll = false;
        this.items = this.items.map((x) => ({
          ...x,
          read: true,
          readAt: x.readAt || new Date().toISOString(),
        }));
        this.setUnread(0);
        this.toast.success(this.i18n.instant(this.markAllOkKey));
      },
      error: () => {
        this.markingAll = false;
        this.toast.error(this.i18n.instant(this.failKey));
      },
    });
  }

  iconFor(n: NotificationItem): string {
    if (n.template?.includes('FAILED') || n.template?.startsWith('OPS_')) {
      return 'error_outline';
    }
    if (n.template === 'TRANSFER_COMPLETED') {
      return 'check_circle';
    }
    return 'notifications';
  }

  isBad(n: NotificationItem): boolean {
    return n.template?.includes('FAILED') === true || n.template?.startsWith('OPS_') === true;
  }

  private reload(): void {
    this.loading = true;
    const req =
      this.mode === 'ops'
        ? this.api.adminOpsNotifications(0, this.pageSize)
        : this.api.myNotifications(0, this.pageSize);
    req.subscribe({
      next: (p) => {
        const items = (p as any).items ?? (p as any).content ?? [];
        this.items = items;
        this.loading = false;
        this.loadedOnce = true;
      },
      error: () => {
        this.items = [];
        this.loading = false;
        this.loadedOnce = true;
        this.toast.error(this.i18n.instant(this.loadFailKey));
      },
    });
  }

  private bindLive(): void {
    if (this.liveSub) {
      return;
    }
    const stream = this.mode === 'ops' ? this.opsStream : this.customerStream;
    this.liveSub = stream.liveEvents$.subscribe((item) => {
      if (!this.items.some((x) => x.id === item.id)) {
        this.items = [item, ...this.items].slice(0, this.pageSize);
      }
    });
  }

  private setUnread(n: number): void {
    if (this.mode === 'ops') {
      this.opsStream.setUnreadCount(n);
    } else {
      this.customerStream.setUnreadCount(n);
    }
  }
}
