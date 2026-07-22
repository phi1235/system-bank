import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { CustomerProfile } from '../../../core/models/domain.model';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

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
    PageHeaderComponent,
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
  rows: CustomerProfile[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  q = '';
  cols = ['fullName', 'email', 'phone', 'kycStatus', 'actions'];
  canKyc$ = this.store.select(selectHasPermission(PERMISSIONS.CUSTOMERS_KYC_DECIDE));

  ngOnInit(): void { this.load(); }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.api.listCustomers(this.pageIndex, this.pageSize, this.q || undefined).subscribe({
      next: (p) => {
        this.rows = p.items || [];
        this.totalElements = p.totalElements ?? this.rows.length;
      },
      error: () => {
        this.rows = [];
        this.totalElements = 0;
      },
    });
  }

  setKyc(c: CustomerProfile, kycStatus: string): void {
    this.api.updateKyc(c.id, kycStatus).subscribe({
      next: (u) => {
        this.rows = this.rows.map((x) => (x.id === u.id ? u : x));
        this.toast.success(this.i18n.instant('ADMIN.KYC_OK', { status: kycStatus }));
      },
    });
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

}
