import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { CollectionOrder, InboundPaymentEvent, VirtualAccount } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-admin-virtual-accounts',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTabsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
  ],
  templateUrl: './admin-virtual-accounts.component.html',
  styleUrl: './admin-virtual-accounts.component.scss',
})
export class AdminVirtualAccountsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  // Tab 0: Inbound Events
  eventColumns: string[] = ['provider', 'providerTransactionId', 'virtualAccountNumber', 'amount', 'status', 'createdAt'];
  events: InboundPaymentEvent[] = [];
  eventsTotal = 0;
  eventsPage = 0;
  eventsSize = 10;
  eventsSearch = '';
  eventsStatus = '';
  eventsLoading = false;

  // Tab 1: Virtual Accounts
  vaColumns: string[] = ['accountNumber', 'bankBin', 'provider', 'mode', 'organizationId', 'status', 'createdAt'];
  vas: VirtualAccount[] = [];
  vasTotal = 0;
  vasPage = 0;
  vasSize = 10;
  vasSearch = '';
  vasStatus = '';
  vasLoading = false;

  // Tab 2: Collection Orders
  orderColumns: string[] = ['merchantOrderId', 'virtualAccountNumber', 'expectedAmount', 'paidAmount', 'status', 'createdAt', 'actions'];
  orders: CollectionOrder[] = [];
  ordersTotal = 0;
  ordersPage = 0;
  ordersSize = 10;
  ordersSearch = '';
  ordersStatus = '';
  ordersLoading = false;

  ngOnInit(): void {
    this.loadEvents();
    this.loadVas();
    this.loadOrders();
  }

  loadEvents(): void {
    this.eventsLoading = true;
    this.api
      .adminSearchInboundEvents({
        q: this.eventsSearch.trim() || undefined,
        status: this.eventsStatus || undefined,
        page: this.eventsPage,
        size: this.eventsSize,
      })
      .subscribe({
        next: (res) => {
          this.events = res.items || [];
          this.eventsTotal = res.totalElements || 0;
          this.eventsLoading = false;
        },
        error: () => (this.eventsLoading = false),
      });
  }

  onEventsPage(e: PageEvent): void {
    this.eventsPage = e.pageIndex;
    this.eventsSize = e.pageSize;
    this.loadEvents();
  }

  loadVas(): void {
    this.vasLoading = true;
    this.api
      .adminSearchVirtualAccounts({
        q: this.vasSearch.trim() || undefined,
        status: this.vasStatus || undefined,
        page: this.vasPage,
        size: this.vasSize,
      })
      .subscribe({
        next: (res) => {
          this.vas = res.items || [];
          this.vasTotal = res.totalElements || 0;
          this.vasLoading = false;
        },
        error: () => (this.vasLoading = false),
      });
  }

  onVasPage(e: PageEvent): void {
    this.vasPage = e.pageIndex;
    this.vasSize = e.pageSize;
    this.loadVas();
  }

  loadOrders(): void {
    this.ordersLoading = true;
    this.api
      .adminSearchCollectionOrders({
        q: this.ordersSearch.trim() || undefined,
        status: this.ordersStatus || undefined,
        page: this.ordersPage,
        size: this.ordersSize,
      })
      .subscribe({
        next: (res) => {
          this.orders = res.items || [];
          this.ordersTotal = res.totalElements || 0;
          this.ordersLoading = false;
        },
        error: () => (this.ordersLoading = false),
      });
  }

  onOrdersPage(e: PageEvent): void {
    this.ordersPage = e.pageIndex;
    this.ordersSize = e.pageSize;
    this.loadOrders();
  }

  adminCompleteOrder(order: CollectionOrder): void {
    if (!confirm(this.i18n.instant('BUSINESS.ORDERS.COMPLETE_CONFIRM'))) return;

    this.api.adminCompleteCollectionOrder(order.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadOrders();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }
}
