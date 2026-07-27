import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminCard } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { selectHasPermission } from '../../../store/auth/auth.selectors';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { RejectCardDialogComponent } from './reject-card-dialog.component';

/** Card approval queue: staff approve (PAN is generated then) or reject with a reason. */
@Component({
  selector: 'app-admin-card-approvals',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    PageHeaderComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './card-approvals.component.html',
  styleUrl: './card-approvals.component.scss',
})
export class AdminCardApprovalsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);

  readonly canDecide$ = this.store.select(
    selectHasPermission(PERMISSIONS.CARDS_APPROVE_EXECUTE),
  );

  rows: AdminCard[] = [];
  loading = false;
  busyId: string | null = null;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  fStatus = 'REQUESTED';

  readonly statusOptions = ['REQUESTED', 'PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'REJECTED', 'CLOSED'];
  cols = ['owner', 'account', 'requestedAt', 'dailyLimit', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.adminCards(this.fStatus, this.pageIndex, this.pageSize).subscribe({
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

  changeStatus(): void {
    this.pageIndex = 0;
    this.load();
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  approve(row: AdminCard): void {
    if (this.busyId) {
      return;
    }
    this.busyId = row.id;
    this.api.adminApproveCard(row.id).subscribe({
      next: (card) => {
        this.busyId = null;
        this.toast.success(
          this.i18n.instant('ADMIN.CARDS_APPROVED', { last4: card.maskedPan?.slice(-4) ?? '' }),
        );
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  openReject(row: AdminCard): void {
    this.dialog
      .open(RejectCardDialogComponent, { data: row, width: '440px' })
      .afterClosed()
      .subscribe((rejected) => {
        if (rejected) {
          this.load();
        }
      });
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? `${id.slice(0, 8)}…` : id;
  }
}
