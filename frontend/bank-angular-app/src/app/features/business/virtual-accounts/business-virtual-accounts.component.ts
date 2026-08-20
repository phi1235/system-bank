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
import { VirtualAccount } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-virtual-accounts',
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
  templateUrl: './business-virtual-accounts.component.html',
  styleUrl: './business-virtual-accounts.component.scss',
})
export class BusinessVirtualAccountsComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  displayedColumns: string[] = ['accountNumber', 'bankBin', 'provider', 'mode', 'customerReference', 'status', 'createdAt', 'actions'];
  virtualAccounts: VirtualAccount[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  searchQuery = '';
  statusFilter = '';
  loading = false;

  // Modal Provision
  showProvisionModal = false;
  provisionMode = 'SINGLE_USE';
  provisionProvider = 'MOCK';
  provisionBankBin = '970422';
  provisionCustomerRef = '';
  provisioning = false;

  // Modal QR View
  showQrModal = false;
  selectedVaForQr: VirtualAccount | null = null;

  ngOnInit(): void {
    this.businessContext.selectedOrg$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadVirtualAccounts();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadVirtualAccounts(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.loading = true;
    this.api
      .listVirtualAccounts(orgId, {
        q: this.searchQuery.trim() || undefined,
        status: this.statusFilter || undefined,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .subscribe({
        next: (res) => {
          this.virtualAccounts = res.items || [];
          this.totalElements = res.totalElements || 0;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  onSearch(): void {
    this.pageIndex = 0;
    this.loadVirtualAccounts();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadVirtualAccounts();
  }

  openProvisionModal(): void {
    this.provisionMode = 'SINGLE_USE';
    this.provisionProvider = 'MOCK';
    this.provisionBankBin = '970422';
    this.provisionCustomerRef = '';
    this.showProvisionModal = true;
  }

  closeProvisionModal(): void {
    this.showProvisionModal = false;
  }

  submitProvision(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.provisioning = true;
    this.api
      .provisionVirtualAccount(orgId, {
        provider: this.provisionProvider,
        bankBin: this.provisionBankBin,
        mode: this.provisionMode,
        customerReference: this.provisionCustomerRef.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.provisioning = false;
          this.showProvisionModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.loadVirtualAccounts();
        },
        error: (err) => {
          this.provisioning = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  openQrModal(va: VirtualAccount): void {
    this.selectedVaForQr = va;
    this.showQrModal = true;
  }

  closeQrModal(): void {
    this.showQrModal = false;
    this.selectedVaForQr = null;
  }

  closeVirtualAccount(va: VirtualAccount): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!confirm(this.i18n.instant('BUSINESS.VA.CLOSE_CONFIRM'))) return;

    this.api.closeVirtualAccount(orgId, va.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadVirtualAccounts();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }
}
