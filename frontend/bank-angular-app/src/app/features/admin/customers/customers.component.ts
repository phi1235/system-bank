import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { take } from 'rxjs';
import { CustomerProfile } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { selectHasAnyPermission, selectHasPermission } from '../../../store/auth/auth.selectors';
import { KycDetailDialogComponent } from './kyc-detail-dialog.component';

type KycStatus = 'NOT_STARTED' | 'PENDING' | 'VERIFIED' | 'REJECTED';

@Component({
  selector: 'app-admin-customers',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatDialogModule,
    MatTooltipModule,
    PageHeaderComponent,
    EnumLabelPipe,
    TranslateModule,
  ],
  templateUrl: './customers.component.html',
  styleUrl: './customers.component.scss',
})
export class AdminCustomersComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);

  rows: CustomerProfile[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  loading = false;
  q = '';
  kycStatus = '';
  cols = ['fullName', 'email', 'phone', 'kycStatus', 'actions'];
  canKycView$ = this.store.select(selectHasAnyPermission([
    PERMISSIONS.CUSTOMERS_KYC_REVIEW,
    PERMISSIONS.CUSTOMERS_KYC_APPROVE,
  ]));
  canKycApprove$ = this.store.select(selectHasPermission(PERMISSIONS.CUSTOMERS_KYC_APPROVE));

  readonly kycOptions: Array<'' | KycStatus> = [
    '', 'NOT_STARTED', 'PENDING', 'VERIFIED', 'REJECTED',
  ];

  ngOnInit(): void {
    const kyc = (this.route.snapshot.queryParamMap.get('kycStatus') || '').toUpperCase();
    if (kyc === 'NOT_STARTED' || kyc === 'PENDING'
        || kyc === 'VERIFIED' || kyc === 'REJECTED') {
      this.kycStatus = kyc;
    }
    this.load();
  }

  get hasActiveFilters(): boolean {
    return !!(this.q.trim() || this.kycStatus);
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.q = '';
    this.kycStatus = '';
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api
      .listCustomers(
        this.pageIndex,
        this.pageSize,
        this.q.trim() || undefined,
        this.kycStatus || undefined,
      )
      .subscribe({
        next: (p) => {
          this.rows = p.items || [];
          this.totalElements = p.totalElements ?? this.rows.length;
          this.loading = false;
        },
        error: (err) => {
          this.rows = [];
          this.totalElements = 0;
          this.loading = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  openKycDetail(customer: CustomerProfile): void {
    this.canKycApprove$.pipe(take(1)).subscribe((canApprove) => {
      this.dialog
        .open(KycDetailDialogComponent, {
          data: { customer, canApprove },
          width: '940px',
          maxWidth: '96vw',
        })
        .afterClosed()
        .subscribe((updated) => {
          if (updated) this.load();
        });
    });
  }
}
