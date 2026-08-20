import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { Settlement } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-settlements',
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
    MatSelectModule,
    MatDividerModule,
    MatDialogModule,
    TranslateModule,
  ],
  templateUrl: './business-settlements.component.html',
  styleUrl: './business-settlements.component.scss',
})
export class BusinessSettlementsComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  displayedColumns: string[] = [
    'collectionOrderId',
    'grossAmount',
    'platformCommission',
    'sellerNetAmount',
    'status',
    'createdAt',
    'actions',
  ];
  settlements: Settlement[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  statusFilter = '';
  loading = false;

  // Detail Modal
  selectedSettlement: Settlement | null = null;
  showDetailModal = false;

  ngOnInit(): void {
    this.businessContext.selectedOrg$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadSettlements();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadSettlements(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.loading = true;
    this.api
      .listSettlements(orgId, {
        status: this.statusFilter || undefined,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .subscribe({
        next: (res) => {
          this.settlements = res.items || [];
          this.totalElements = res.totalElements || 0;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  onFilterChange(): void {
    this.pageIndex = 0;
    this.loadSettlements();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadSettlements();
  }

  openDetailModal(st: Settlement): void {
    this.selectedSettlement = st;
    this.showDetailModal = true;
  }

  closeDetailModal(): void {
    this.showDetailModal = false;
    this.selectedSettlement = null;
  }

  retryFailedLegs(st: Settlement): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.retrySettlement(orgId, st.id).subscribe({
      next: (res) => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadSettlements();
        if (this.selectedSettlement?.id === st.id) {
          this.selectedSettlement = res;
        }
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }
}
