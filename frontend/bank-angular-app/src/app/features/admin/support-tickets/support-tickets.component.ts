import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
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
import { filter, switchMap } from 'rxjs';
import { SupportTicket } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { selectHasPermission, selectUser } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-admin-support-tickets',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    TranslateModule,
    PageHeaderComponent,
  ],
  templateUrl: './support-tickets.component.html',
  styleUrl: './support-tickets.component.scss',
})
export class AdminSupportTicketsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);

  readonly categories = ['', 'GENERAL', 'ACCOUNT', 'TRANSFER', 'CARD', 'KYC', 'SECURITY', 'OTHER'];
  readonly statuses = ['', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'];
  readonly cols = ['createdAt', 'subject', 'category', 'priority', 'status', 'requesterEmail', 'actions'];

  rows: SupportTicket[] = [];
  selected: SupportTicket | null = null;
  loading = false;
  busyId: string | null = null;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  q = '';
  status = 'OPEN';
  category = '';
  resolutionNote = '';
  rejectReason = '';

  canClaim$ = this.store.select(selectHasPermission(PERMISSIONS.SUPPORT_TICKETS_CLAIM));
  canDecide$ = this.store.select(selectHasPermission(PERMISSIONS.SUPPORT_TICKETS_DECIDE));
  currentUserId: string | null = null;

  ngOnInit(): void {
    this.store.select(selectUser).subscribe((u) => {
      this.currentUserId = u?.userId ?? null;
    });
    this.load();
  }

  /** Optional take-ownership (OPEN only). Not required before decide. */
  canShowClaim(t: SupportTicket | null): boolean {
    return !!t && (t.status || '').toUpperCase() === 'OPEN';
  }

  /** Staff with decide may resolve/reject OPEN or IN_PROGRESS (same person OK). */
  canShowDecide(t: SupportTicket | null): boolean {
    if (!t || !this.currentUserId) return false;
    if (t.userId === this.currentUserId) return false;
    const s = (t.status || '').toUpperCase();
    return s === 'OPEN' || s === 'IN_PROGRESS';
  }

  get hasActiveFilters(): boolean {
    return !!(this.q.trim() || this.status || this.category);
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.q = '';
    this.status = '';
    this.category = '';
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api
      .adminSupportTickets(this.pageIndex, this.pageSize, {
        status: this.status || undefined,
        category: this.category || undefined,
        q: this.q.trim() || undefined,
      })
      .subscribe({
        next: (p) => {
          this.rows = p.items || [];
          this.totalElements = p.totalElements ?? this.rows.length;
          this.loading = false;
          if (this.selected) {
            const still = this.rows.find((r) => r.id === this.selected?.id);
            this.selected = still || this.selected;
          }
        },
        error: (err) => {
          this.rows = [];
          this.totalElements = 0;
          this.loading = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  openDetail(row: SupportTicket): void {
    this.selected = row;
    this.resolutionNote = row.resolutionNote || '';
    this.rejectReason = '';
    this.api.adminSupportTicket(row.id).subscribe({
      next: (t) => {
        this.selected = t;
        this.resolutionNote = t.resolutionNote || '';
      },
      error: (err) => this.toast.error(resolveHttpErrorMessage(err, this.i18n)),
    });
  }

  isOpen(t: SupportTicket | null): boolean {
    const s = (t?.status || '').toUpperCase();
    return s === 'OPEN' || s === 'IN_PROGRESS';
  }

  claim(t: SupportTicket): void {
    this.busyId = t.id;
    this.api.claimSupportTicket(t.id).subscribe({
      next: (updated) => {
        this.busyId = null;
        this.toast.success(this.i18n.instant('ADMIN.SUPPORT_CLAIM_OK'));
        this.selected = updated;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  resolve(t: SupportTicket): void {
    const data: ConfirmDialogData = {
      title: this.i18n.instant('ADMIN.SUPPORT_RESOLVE_TITLE'),
      message: this.i18n.instant('ADMIN.SUPPORT_RESOLVE_MSG', { subject: t.subject }),
      confirmLabel: this.i18n.instant('ADMIN.SUPPORT_RESOLVE'),
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busyId = t.id;
          return this.api.resolveSupportTicket(t.id, this.resolutionNote.trim() || undefined);
        }),
      )
      .subscribe({
        next: (updated) => {
          this.busyId = null;
          this.toast.success(this.i18n.instant('ADMIN.SUPPORT_RESOLVE_OK'));
          this.selected = updated;
          this.load();
        },
        error: (err) => {
          this.busyId = null;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  reject(t: SupportTicket): void {
    const reason = this.rejectReason.trim();
    if (!reason) {
      this.toast.error(this.i18n.instant('ADMIN.SUPPORT_REJECT_REQUIRED'));
      return;
    }
    const data: ConfirmDialogData = {
      title: this.i18n.instant('ADMIN.SUPPORT_REJECT_TITLE'),
      message: this.i18n.instant('ADMIN.SUPPORT_REJECT_MSG', { subject: t.subject }),
      confirmLabel: this.i18n.instant('ADMIN.REJECT'),
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busyId = t.id;
          return this.api.rejectSupportTicket(t.id, reason);
        }),
      )
      .subscribe({
        next: (updated) => {
          this.busyId = null;
          this.toast.success(this.i18n.instant('ADMIN.SUPPORT_REJECT_OK'));
          this.selected = updated;
          this.rejectReason = '';
          this.load();
        },
        error: (err) => {
          this.busyId = null;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  statusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'OPEN':
        return 'warn';
      case 'IN_PROGRESS':
        return 'info';
      case 'RESOLVED':
        return 'ok';
      case 'REJECTED':
        return 'bad';
      default:
        return '';
    }
  }
}
