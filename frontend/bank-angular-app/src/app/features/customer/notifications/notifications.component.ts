import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotificationItem } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-notifications',
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
export class NotificationsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  items: NotificationItem[] = [];
  loading = false;
  markingAll = false;
  pageIndex = 0;
  pageSize = 10;
  total = 0;
  unread = 0;

  ngOnInit(): void {
    this.reload();
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
    this.api.markNotificationRead(item.id).subscribe({
      next: (updated) => {
        this.items = this.items.map((x) => (x.id === updated.id ? updated : x));
        this.unread = Math.max(0, this.unread - 1);
      },
      error: () => this.toast.error(this.i18n.instant('CUSTOMER.NOTIF_ACTION_FAIL')),
    });
  }

  markAll(): void {
    if (this.markingAll || this.unread === 0) {
      return;
    }
    this.markingAll = true;
    this.api.markAllNotificationsRead().subscribe({
      next: () => {
        this.markingAll = false;
        this.items = this.items.map((x) => ({
          ...x,
          read: true,
          readAt: x.readAt || new Date().toISOString(),
        }));
        this.unread = 0;
        this.toast.success(this.i18n.instant('CUSTOMER.NOTIF_MARK_ALL_OK'));
      },
      error: () => {
        this.markingAll = false;
        this.toast.error(this.i18n.instant('CUSTOMER.NOTIF_ACTION_FAIL'));
      },
    });
  }

  private reload(): void {
    this.loading = true;
    this.api.myNotifications(this.pageIndex, this.pageSize).subscribe({
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
        this.toast.error(this.i18n.instant('CUSTOMER.NOTIF_LOAD_FAIL'));
      },
    });
    this.api.notificationUnreadCount().subscribe({
      next: (r) => {
        this.unread = r?.unread ?? 0;
      },
      error: () => {
        this.unread = 0;
      },
    });
  }
}
