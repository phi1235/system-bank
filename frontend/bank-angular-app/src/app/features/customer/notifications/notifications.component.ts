import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { NotificationItem } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { NotificationStreamService } from '../../../core/services/notification-stream.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  resolveNotificationPath,
  humanizeNotificationBody,
  humanizeTemplateCode,
} from '../../../core/utils/notification-link.util';
import { Router } from '@angular/router';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

type ReadFilter = 'ALL' | 'UNREAD' | 'READ';

@Component({
  selector: 'app-customer-notifications',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatPaginatorModule,
    MatTooltipModule,
    TranslateModule,
    PageHeaderComponent,
    LoadingComponent,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class CustomerNotificationsComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly stream = inject(NotificationStreamService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly router = inject(Router);
  private liveSub?: Subscription;

  items: NotificationItem[] = [];
  loading = false;
  markingAll = false;
  markingId: string | null = null;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  readFilter: ReadFilter = 'ALL';

  get unreadCount(): number {
    return this.stream.unreadCount;
  }

  get hasActiveFilters(): boolean {
    return this.readFilter !== 'ALL';
  }

  ngOnInit(): void {
    this.reload();
    this.liveSub = this.stream.liveEvents$.subscribe((item) => {
      // Prepend live items only when they match current view
      if (this.pageIndex !== 0) {
        return;
      }
      if (this.readFilter === 'READ') {
        return;
      }
      if (this.items.some((x) => x.id === item.id)) {
        return;
      }
      this.items = [item, ...this.items].slice(0, this.pageSize);
      this.totalElements += 1;
    });
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.reload();
  }

  resetFilters(): void {
    this.readFilter = 'ALL';
    this.pageIndex = 0;
    this.reload();
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.api.myNotifications(this.pageIndex, this.pageSize, this.readFilter).subscribe({
      next: (p) => {
        this.items = p.items || [];
        this.totalElements = p.totalElements ?? this.items.length;
        this.loading = false;
      },
      error: (err) => {
        this.items = [];
        this.totalElements = 0;
        this.loading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
    this.api.notificationUnreadCount().subscribe({
      next: (r) => this.stream.setUnreadCount(r?.unread ?? 0),
      error: () => {},
    });
  }

  markRead(item: NotificationItem): void {
    if (item.read || this.markingId) {
      return;
    }
    this.markingId = item.id;
    this.api.markNotificationRead(item.id).subscribe({
      next: (updated) => {
        this.markingId = null;
        if (this.readFilter === 'UNREAD') {
          this.items = this.items.filter((x) => x.id !== updated.id);
          this.totalElements = Math.max(0, this.totalElements - 1);
        } else {
          this.items = this.items.map((x) => (x.id === updated.id ? updated : x));
        }
        this.stream.setUnreadCount(Math.max(0, this.unreadCount - 1));
      },
      error: (err) => {
        this.markingId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  openItem(item: NotificationItem): void {
    const path = resolveNotificationPath(item, 'customer');
    const go = () => {
      if (path) {
        void this.router.navigateByUrl(path);
      }
    };
    if (item.read) {
      go();
      return;
    }
    this.markingId = item.id;
    this.api.markNotificationRead(item.id).subscribe({
      next: (updated) => {
        this.markingId = null;
        this.items = this.items.map((x) => (x.id === updated.id ? { ...item, ...updated, read: true } : x));
        this.stream.setUnreadCount(Math.max(0, this.unreadCount - 1));
        go();
      },
      error: (err) => {
        this.markingId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        go();
      },
    });
  }

  markAll(): void {
    if (this.markingAll || this.unreadCount === 0) {
      return;
    }
    this.markingAll = true;
    this.api.markAllNotificationsRead().subscribe({
      next: () => {
        this.markingAll = false;
        this.stream.setUnreadCount(0);
        this.toast.success(this.i18n.instant('CUSTOMER.NOTIF_MARK_ALL_OK'));
        this.reload();
      },
      error: (err) => {
        this.markingAll = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  displayBody(n: NotificationItem): string {
    return humanizeNotificationBody(n.body, n.template);
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
      return 'Thông báo';
    }
    const key = `NOTIF_TEMPLATE.${tpl}`;
    const loc = this.i18n.instant(key);
    if (loc && loc !== key) {
      return loc;
    }
    return humanizeTemplateCode(tpl);
  }

  transferIdFromBody(body: string | null | undefined): string | null {
    if (!body) {
      return null;
    }
    const m = body.match(
      /transfer\s+([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/i,
    );
    return m?.[1] ?? null;
  }
}
