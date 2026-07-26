import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ReconItem, ReconRun } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { selectHasPermission } from '../../../store/auth/auth.selectors';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';

@Component({
  selector: 'app-admin-reconciliation',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './reconciliation.component.html',
  styleUrl: './reconciliation.component.scss',
})
export class AdminReconciliationComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);

  readonly canExecute$ = this.store.select(selectHasPermission(PERMISSIONS.TX_RECON_EXECUTE));

  rows: ReconRun[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  loading = false;
  running = false;
  runDate = '';

  selectedRun: ReconRun | null = null;
  items: ReconItem[] = [];
  itemsLoading = false;

  runCols = ['businessDate', 'status', 'triggerType', 'ordersChecked', 'discrepancyCount', 'startedAt'];
  itemCols = ['kind', 'transferId', 'entryRef', 'expectedAmount', 'actualAmount', 'detail'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.adminReconRuns(this.pageIndex, this.pageSize).subscribe({
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

  runNow(): void {
    if (!this.runDate || this.running) {
      return;
    }
    this.running = true;
    this.api.adminRunRecon(this.runDate).subscribe({
      next: (run) => {
        this.running = false;
        this.toast.success(
          this.i18n.instant('ADMIN.RECON_RUN_DONE', { status: run.status, count: run.discrepancyCount }),
        );
        this.pageIndex = 0;
        this.load();
        this.openDetail(run);
      },
      error: (err) => {
        this.running = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  openDetail(run: ReconRun): void {
    this.selectedRun = run;
    this.items = [];
    if (run.discrepancyCount === 0) {
      return;
    }
    this.itemsLoading = true;
    this.api.adminReconRun(run.id).subscribe({
      next: (detail) => {
        this.selectedRun = detail.run;
        this.items = detail.items;
        this.itemsLoading = false;
      },
      error: (err) => {
        this.itemsLoading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? `${id.slice(0, 8)}…` : id;
  }
}
