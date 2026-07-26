import { Overlay, OverlayModule, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  Injector,
  Input,
  OnDestroy,
  TemplateRef,
  ViewChild,
  ViewContainerRef,
  inject,
} from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';
import { NotificationItem } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { NotificationStreamService } from '../../../core/services/notification-stream.service';
import { OpsNotificationStreamService } from '../../../core/services/ops-notification-stream.service';
import { ToastService } from '../../../core/services/toast.service';
import {
    resolveNotificationPath,
    humanizeNotificationBody,
    humanizeTemplateCode,
  } from '../../../core/utils/notification-link.util';

export type NotificationBellMode = 'customer' | 'ops';

/**
 * Header notification bell + wide dropdown.
 * Uses CDK Overlay with global right-edge positioning (not mat-menu),
 * so the panel is free of Material's 280px cap and expands under the top-right header.
 *
 * Streams are resolved lazily by mode so admin shell does not hard-depend on
 * the customer NotificationStreamService at construction time (and vice versa).
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    CommonModule,
    OverlayModule,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent implements OnDestroy {
  @Input({ required: true }) mode!: NotificationBellMode;
  @ViewChild('triggerBtn', { read: ElementRef }) private triggerBtn?: ElementRef<HTMLElement>;
  @ViewChild('panelTpl') private panelTpl?: TemplateRef<unknown>;

  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly injector = inject(Injector);
  private readonly overlay = inject(Overlay);
  private readonly vcr = inject(ViewContainerRef);
  private readonly router = inject(Router);
  private liveSub?: Subscription;
  private overlayRef?: OverlayRef;
  private backdropSub?: Subscription;

  items: NotificationItem[] = [];
  loading = false;
  markingAll = false;
  loadedOnce = false;
  open = false;
  private readonly pageSize = 12;
  private readonly panelMaxWidth = 720;
  private readonly edgeMargin = 12;

  get unread$(): Observable<number> {
    return this.stream().unreadCount$;
  }

  get unread(): number {
    return this.stream().unreadCount;
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

  get viewAllKey(): string {
    return this.mode === 'ops' ? 'ADMIN.NOTIF_VIEW_ALL' : 'CUSTOMER.NOTIF_VIEW_ALL';
  }

  /** Full inbox page (customer only; ops stays panel-only for now). */
  get viewAllLink(): string | null {
    return this.mode === 'customer' ? '/customer/notifications' : null;
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
    this.closePanel();
  }

  togglePanel(event?: MouseEvent): void {
    event?.stopPropagation();
    if (this.open) {
      this.closePanel();
      return;
    }
    this.openPanel();
  }

  openPanel(): void {
    if (!this.panelTpl || this.open) {
      return;
    }

    const margin = this.edgeMargin;
    const width = Math.min(this.panelMaxWidth, Math.max(320, window.innerWidth - margin * 2));
    const triggerBottom = this.triggerBtn?.nativeElement.getBoundingClientRect().bottom ?? 56;
    const top = Math.round(triggerBottom + 8);

    // Global strategy pins to viewport right edge — expands left from the screen edge.
    const positionStrategy = this.overlay
      .position()
      .global()
      .right(`${margin}px`)
      .top(`${top}px`);

    this.overlayRef = this.overlay.create({
      positionStrategy,
      scrollStrategy: this.overlay.scrollStrategies.reposition(),
      width,
      maxWidth: `calc(100vw - ${margin * 2}px)`,
      hasBackdrop: true,
      backdropClass: 'cdk-overlay-transparent-backdrop',
      panelClass: 'notif-dropdown-overlay',
      disposeOnNavigation: true,
    });

    this.overlayRef.attach(new TemplatePortal(this.panelTpl, this.vcr));
    this.backdropSub = this.overlayRef.backdropClick().subscribe(() => this.closePanel());
    this.overlayRef.keydownEvents().subscribe((e) => {
      if (e.key === 'Escape') {
        this.closePanel();
      }
    });

    this.open = true;
    this.bindLive();
    this.reload();
  }

  closePanel(): void {
    this.backdropSub?.unsubscribe();
    this.backdropSub = undefined;
    this.overlayRef?.dispose();
    this.overlayRef = undefined;
    this.open = false;
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

  /** Mark read (if needed) then navigate to deep-linked entity screen. */
  openItem(item: NotificationItem, event?: MouseEvent): void {
    event?.stopPropagation();
    const path = resolveNotificationPath(item, this.mode === 'ops' ? 'ops' : 'customer');
    const after = () => {
      if (path) {
        this.closePanel();
        void this.router.navigateByUrl(path);
      }
    };
    if (item.read) {
      after();
      return;
    }
    const req =
      this.mode === 'ops'
        ? this.api.markAdminOpsNotificationRead(item.id)
        : this.api.markNotificationRead(item.id);
    req.subscribe({
      next: (updated) => {
        this.items = this.items.map((x) =>
          x.id === updated.id ? { ...item, ...updated, read: true } : x,
        );
        this.setUnread(Math.max(0, this.unread - 1));
        after();
      },
      error: () => {
        this.toast.error(this.i18n.instant(this.failKey));
        after();
      },
    });
  }

  hasLink(item: NotificationItem): boolean {
    return !!resolveNotificationPath(item, this.mode === 'ops' ? 'ops' : 'customer');
  }

  displayBody(item: NotificationItem): string {
    return humanizeNotificationBody(item.body, item.template);
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

  templateLabel(n: NotificationItem): string {
    const tpl = (n.template || '').trim();
    if (!tpl) {
      return this.i18n.instant('COMMON.NOTIFICATION');
    }
    const key = `NOTIF_TEMPLATE.${tpl}`;
    const loc = this.i18n.instant(key);
    if (loc && loc !== key) {
      return loc;
    }
    // Never show raw OPS_SUPPORT_TICKET_OPENED-style codes to operators.
    return humanizeTemplateCode(tpl);
  }

  openViewAll(event?: MouseEvent): void {
    event?.stopPropagation();
    const link = this.viewAllLink;
    if (!link) {
      return;
    }
    this.closePanel();
    void this.router.navigateByUrl(link);
  }

  private stream(): NotificationStreamService | OpsNotificationStreamService {
    return this.mode === 'ops'
      ? this.injector.get(OpsNotificationStreamService)
      : this.injector.get(NotificationStreamService);
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
    this.liveSub = this.stream().liveEvents$.subscribe((item) => {
      if (!this.items.some((x) => x.id === item.id)) {
        this.items = [item, ...this.items].slice(0, this.pageSize);
      }
    });
  }

  private setUnread(n: number): void {
    this.stream().setUnreadCount(n);
  }
}
