import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { forkJoin, of, type Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { OutboxCounts } from '../../../core/models/domain.model';
import { PageResponse } from '../../../core/models/api.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  selectHasAnyPermission,
  selectHasPermission,
} from '../../../store/auth/auth.selectors';

export interface DashboardKpis {
  customers: number | null;
  kycPending: number | null;
  accounts: number | null;
  accountsFrozen: number | null;
  transfers: number | null;
  transfersFailed: number | null;
  transfersCompensated: number | null;
  outboxDead: number | null;
  outboxPending: number | null;
  outboxPublished: number | null;
  users: number | null;
  usersLocked: number | null;
  audits: number | null;
}

function emptyKpis(): DashboardKpis {
  return {
    customers: null,
    kycPending: null,
    accounts: null,
    accountsFrozen: null,
    transfers: null,
    transfersFailed: null,
    transfersCompensated: null,
    outboxDead: null,
    outboxPending: null,
    outboxPublished: null,
    users: null,
    usersLocked: null,
    audits: null,
  };
}

function totalOf(page: { totalElements?: number } | null | undefined): number {
  return page?.totalElements ?? 0;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);

  readonly canCustomers$ = this.store.select(selectHasPermission(PERMISSIONS.CUSTOMERS_LIST_VIEW));
  readonly canAccounts$ = this.store.select(selectHasPermission(PERMISSIONS.ACCOUNTS_LOOKUP_VIEW));
  readonly canTx$ = this.store.select(selectHasPermission(PERMISSIONS.TX_LIST_VIEW));
  readonly canUsers$ = this.store.select(
    selectHasAnyPermission([
      PERMISSIONS.USERS_PASSWORD_RESET,
      PERMISSIONS.USERS_LOCK_EXECUTE,
      PERMISSIONS.RBAC_USERS_ASSIGN,
      PERMISSIONS.RBAC_ACCESS,
    ]),
  );
  readonly canAudit$ = this.store.select(selectHasPermission(PERMISSIONS.AUDIT_LIST_VIEW));
  readonly canRbac$ = this.store.select(selectHasPermission(PERMISSIONS.RBAC_ACCESS));

  loading = false;
  partialError = false;
  lastUpdated: Date | null = null;
  kpis: DashboardKpis = emptyKpis();

  ngOnInit(): void {
    this.refresh();
  }

  display(value: number | null): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return String(value);
  }

  get failedReviewTotal(): number | null {
    const a = this.kpis.transfersFailed;
    const b = this.kpis.transfersCompensated;
    if (a === null && b === null) {
      return null;
    }
    return (a ?? 0) + (b ?? 0);
  }

  get hasAnyLiveKpi(): boolean {
    return Object.values(this.kpis).some((v) => v !== null);
  }

  refresh(): void {
    this.loading = true;
    this.partialError = false;

    this.api.getDashboardSummary().subscribe({
      next: (kpis) => {
        this.kpis = kpis;
        this.loading = false;
        this.lastUpdated = new Date();
      },
      error: (err) => {
        this.loading = false;
        this.kpis = emptyKpis();
        this.partialError = true;
        this.toast.error(
          resolveHttpErrorMessage(err, this.i18n) || this.i18n.instant('ADMIN.DASH_LOAD_FAIL'),
        );
      },
    });
  }
}
