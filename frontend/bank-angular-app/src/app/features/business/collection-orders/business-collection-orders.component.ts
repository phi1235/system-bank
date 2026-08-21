import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { CollectionOrder, SplitRule } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-collection-orders',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
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
  templateUrl: './business-collection-orders.component.html',
  styleUrl: './business-collection-orders.component.scss',
})
export class BusinessCollectionOrdersComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  displayedColumns: string[] = [
    'merchantOrderId',
    'virtualAccountNumber',
    'expectedAmount',
    'paidAmount',
    'status',
    'createdAt',
    'actions',
  ];
  orders: CollectionOrder[] = [];
  splitRules: SplitRule[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  searchQuery = '';
  statusFilter = '';
  loading = false;

  // Modal Create Order
  showCreateModal = false;
  newMerchantOrderId = '';
  newExpectedAmount: number | null = null;
  newCustomerRef = '';
  newSplitRuleId = '';
  creatingOrder = false;

  // Modal QR View
  showQrModal = false;
  selectedOrderForQr: CollectionOrder | null = null;

  get canCreateOrder(): boolean {
    return this.businessContext.hasPermission('collection:create') || this.businessContext.hasPermission('collection:manage') ||
           this.businessContext.hasPermission('orders:create') || this.businessContext.hasPermission('orders:manage');
  }

  ngOnInit(): void {
    this.businessContext.selectedOrg$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadOrders();
      if (this.businessContext.hasPermission('split:view') || this.businessContext.hasPermission('split:manage')) {
        this.loadSplitRules();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadOrders(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.loading = true;
    this.api
      .listCollectionOrders(orgId, {
        q: this.searchQuery.trim() || undefined,
        status: this.statusFilter || undefined,
      })
      .subscribe({
        next: (res) => {
          this.orders = res || [];
          this.totalElements = this.orders.length;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  loadSplitRules(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;
    this.api.listSplitRules(orgId).subscribe({
      next: (rules) => (this.splitRules = rules),
      error: () => {},
    });
  }

  onSearch(): void {
    this.loadOrders();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadOrders();
  }

  openCreateModal(): void {
    this.newMerchantOrderId = 'ORD_' + Math.floor(100000 + Math.random() * 900000);
    this.newExpectedAmount = null;
    this.newCustomerRef = '';
    this.newSplitRuleId = '';
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  submitCreateOrder(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!this.newMerchantOrderId.trim() || !this.newExpectedAmount || this.newExpectedAmount <= 0) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }

    this.creatingOrder = true;
    this.api
      .createCollectionOrder(orgId, {
        merchantOrderId: this.newMerchantOrderId.trim(),
        expectedAmount: this.newExpectedAmount,
        currency: 'VND',
        customerReference: this.newCustomerRef.trim() || undefined,
        splitRuleId: this.newSplitRuleId || undefined,
      })
      .subscribe({
        next: (order) => {
          this.creatingOrder = false;
          this.showCreateModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.loadOrders();
          this.openQrModal(order);
        },
        error: (err) => {
          this.creatingOrder = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  openQrModal(order: CollectionOrder): void {
    this.selectedOrderForQr = order;
    this.showQrModal = true;
  }

  closeQrModal(): void {
    this.showQrModal = false;
    this.selectedOrderForQr = null;
  }

  cancelOrder(order: CollectionOrder): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.cancelCollectionOrder(orgId, order.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadOrders();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }

  completeOrder(order: CollectionOrder): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!confirm(this.i18n.instant('BUSINESS.ORDERS.COMPLETE_CONFIRM'))) return;

    this.api.completeCollectionOrder(orgId, order.id).subscribe({
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
