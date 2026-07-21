import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { NotificationItem } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { OpsNotificationStreamService } from '../../../core/services/ops-notification-stream.service';
import { ToastService } from '../../../core/services/toast.service';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-admin-notifications',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatPaginatorModule,
    PageHeaderComponent,
    LoadingComponent,
    TranslateModule,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class AdminNotificationsComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly stream = inject(OpsNotificationStreamService);
  private liveSub?: Subscription;
  private unreadSub?: Subscription;

  items: NotificationItem[] = [];
  loading = false;
  markingAll = false;
  pageIndex = 0;
  pageSize = 10;
  total = 0;
  unread = 0;

  ngOnInit(): void {
    this.reload();
    this.unreadSub = this.stream.unreadCount$.subscribe((n) => {
      this.unread = n;
    });
    this.liveSub = this.stream.liveEvents$.subscribe((item) => {
      if (this.pageIndex === 0) {
        if (!this.items.some((x) => x.id === item.id)) {
          this.items = [item, ...this.items].slice(0, this.pageSize);
          this.total += 1;
        }
      } else {
        this.total += 1;
      }
    });
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
    this.unreadSub?.unsubscribe();
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.reload();
  }

  markRead(item: NotificationItem): void {
    if (item.read) {
      return;
    }
    this.api.markAdminOpsNotificationRead(item.id).subscribe({
      next: (updated) => {
        this.items = this.items.map((x) => (x.id === updated.id ? updated : x));
        this.stream.setUnreadCount(Math.max(0, this.stream.unreadCount - 1));
      },
      error: () => this.toast.error(this.i18n.instant('ADMIN.NOTIF_ACTION_FAIL')),
    });
  }

  markAll(): void {
    if (this.markingAll || this.unread === 0) {
      return;
    }
    this.markingAll = true;
    this.api.markAllAdminOpsNotificationsRead().subscribe({
      next: () => {
        this.markingAll = false;
        this.items = this.items.map((x) => ({
          ...x,
          read: true,
          readAt: x.readAt || new Date().toISOString(),
        }));
        this.stream.setUnreadCount(0);
        this.toast.success(this.i18n.instant('ADMIN.NOTIF_MARK_ALL_OK'));
      },
      error: () => {
        this.markingAll = false;
        this.toast.error(this.i18n.instant('ADMIN.NOTIF_ACTION_FAIL'));
      },
    });
  }

  private reload(): void {
    this.loading = true;
    this.api.adminOpsNotifications(this.pageIndex, this.pageSize).subscribe({
      next: (p) => {
        const items = (p as any).items ?? (p as any).content ?? [];
        this.items = items;
        this.total = (p as any).totalElements ?? items.length;
        this.loading = false;
      },
      error: () => {
        this.items = [];
        this.total = 0;
        this.loading = false;
        this.toast.error(this.i18n.instant('ADMIN.NOTIF_LOAD_FAIL'));
      },
    });
    this.api.adminOpsNotificationUnreadCount().subscribe({
      next: (r) => this.stream.setUnreadCount(r?.unread ?? 0),
      error: () => this.stream.setUnreadCount(0),
    });
  }
}
