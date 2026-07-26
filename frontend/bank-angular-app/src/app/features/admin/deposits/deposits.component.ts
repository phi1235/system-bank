import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DepositAdminSummary, DepositProduct } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { selectHasPermission } from '../../../store/auth/auth.selectors';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { EditDepositProductDialogComponent } from './edit-product-dialog.component';

/** Funding overview: KPIs + product rate sheet + batch trigger. Drill-down lives on /contracts. */
@Component({
  selector: 'app-admin-deposits',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    RouterLink,
    PageHeaderComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './deposits.component.html',
  styleUrl: './deposits.component.scss',
})
export class AdminDepositsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);

  readonly canRunBatch$ = this.store.select(
    selectHasPermission(PERMISSIONS.DEPOSITS_BATCH_EXECUTE),
  );
  readonly canManageProducts$ = this.store.select(
    selectHasPermission(PERMISSIONS.DEPOSITS_PRODUCTS_MANAGE),
  );

  summary: DepositAdminSummary | null = null;
  products: DepositProduct[] = [];
  loading = false;
  running = false;

  productCols = ['tenor', 'rate', 'earlyRate', 'min', 'active', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.adminDepositSummary().subscribe({
      next: (s) => {
        this.summary = s;
        this.loading = false;
      },
      error: (err) => {
        this.summary = null;
        this.loading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
    this.api.adminAllDepositProducts().subscribe({
      next: (p) => (this.products = p),
      error: () => (this.products = []),
    });
  }

  runBatch(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    this.api.adminRunDepositBatch().subscribe({
      next: (r) => {
        this.running = false;
        this.toast.success(
          this.i18n.instant('ADMIN.DEPOSITS_BATCH_DONE', {
            accrued: r.accruedUpdated,
            matured: r.matured,
            failed: r.failed,
          }),
        );
        this.load();
      },
      error: (err) => {
        this.running = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  editProduct(p: DepositProduct): void {
    this.dialog
      .open(EditDepositProductDialogComponent, { data: p, width: '420px' })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) {
          this.load();
        }
      });
  }

  ratePct(bps: number): string {
    return (bps / 100).toFixed(2).replace(/\.?0+$/, '');
  }
}
