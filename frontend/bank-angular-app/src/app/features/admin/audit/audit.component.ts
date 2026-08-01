import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
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
import { AuditLog } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { AuditDetailDialogComponent } from '../../../shared/components/audit-detail-dialog/audit-detail-dialog.component';
import { exportToCsv, exportToCsvWithQueue } from '../../../core/utils/csv-export.util';
import { ExportQueueService } from '../../../core/services/export-queue.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatTooltipModule,
    MatDialogModule,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AdminAuditComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);

  rows: AuditLog[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  loading = false;
  openingId: string | null = null;
  cols = ['createdAt', 'action', 'accountNo', 'actorUserId', 'resourceType', 'ip', 'actions'];
  userMap = new Map<string, string>();
  accountMap = new Map<string, string>();

  readonly actionOptions = [
    '',
    'TRANSFER_CREATE',
    'ACCOUNT_FREEZE',
    'ACCOUNT_UNFREEZE',
    'ACCOUNT_TOPUP',
    'USER_ROLE_ASSIGN',
    'RBAC_ROLE_CREATE',
    'RBAC_ROLE_UPDATE',
    'RBAC_PERMISSION_UPDATE',
    'USER_LOCK',
    'USER_UNLOCK',
    'USER_PASSWORD_RESET',
    'OUTBOX_REPLAY',
    'LOGIN',
    'LOGOUT',
    'REGISTER'
  ];
  readonly resourceTypeOptions = [
    '',
    'TRANSFER',
    'ACCOUNT',
    'USER',
    'ROLE',
    'PERMISSION',
    'OUTBOX_EVENT',
    'SESSION'
  ];

  form = this.fb.nonNullable.group({
    action: [''],
    resourceType: [''],
    accountNo: [''],
    actorUsername: [''],
    from: [''],
    to: [''],
  });

  ngOnInit(): void {
    this.api.rbacUsers(0, 100).subscribe({
      next: (p) => {
        (p.items || []).forEach((u) => {
          if (u.userId) {
            this.userMap.set(u.userId, u.username || u.email || u.userId);
          }
        });
      },
    });
    this.api.adminListAccounts(0, 200).subscribe({
      next: (p) => {
        (p.items || []).forEach((acc) => {
          if (acc.id && acc.accountNumber) {
            this.accountMap.set(acc.id, acc.accountNumber);
          }
        });
      },
    });
    this.load();
  }

  getActorName(userId: string | null | undefined): string {
    if (!userId) return 'Hệ thống';
    return this.userMap.get(userId) || (userId.length > 8 ? userId.slice(0, 8) + '…' : userId);
  }

  getAccountNo(a: AuditLog): string {
    if (!a) return '—';
    if (a.resourceId && this.accountMap.has(a.resourceId)) {
      return this.accountMap.get(a.resourceId)!;
    }
    if (a.metadata) {
      try {
        const meta = typeof a.metadata === 'string' ? JSON.parse(a.metadata) : a.metadata;
        const stk = meta.accountNumber || meta.stk || meta.toAccountNumber || meta.fromAccountNumber || meta.account;
        if (stk) return String(stk);
      } catch (e) {
        // ignore
      }
    }
    if (a.resourceId && (a.resourceId.length <= 14 || /^\d+$/.test(a.resourceId))) {
      return a.resourceId;
    }
    return '—';
  }

  get hasActiveFilters(): boolean {
    const v = this.form.getRawValue();
    return !!(v.action || v.resourceType || v.accountNo.trim() || v.actorUsername.trim() || v.from || v.to);
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.form.reset({
      action: '',
      resourceType: '',
      accountNo: '',
      actorUsername: '',
      from: '',
      to: '',
    });
    this.pageIndex = 0;
    this.load();
  }

  private readonly exportQueue = inject(ExportQueueService);

  exportCsv(): void {
    const data: ConfirmDialogData = {
      title: this.i18n.instant('COMMON.EXPORT_CONFIRM_TITLE'),
      message: this.i18n.instant('COMMON.EXPORT_CONFIRM_MSG'),
      confirmText: this.i18n.instant('COMMON.EXPORT_CONFIRM_BTN'),
      cancelText: this.i18n.instant('COMMON.CANCEL'),
    };

    this.dialog
      .open(ConfirmDialogComponent, { data, width: '460px' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.startAsyncExportAudit();
        }
      });
  }

  private startAsyncExportAudit(): void {
    const v = this.form.getRawValue();
    const filters = {
      action: v.action || undefined,
      resourceType: v.resourceType || undefined,
      actorUserId: v.actorUsername.trim() || undefined,
      resourceId: v.accountNo.trim() || undefined,
      from: this.toIsoStart(v.from),
      to: this.toIsoEnd(v.to),
    };

    this.api.auditLogs(0, 1, filters).subscribe({
      next: (initial) => {
        const totalElements = initial.totalElements ?? 0;
        if (totalElements === 0) {
          this.toast.info(this.i18n.instant('COMMON.NO_DATA_TO_EXPORT') || 'Không có dữ liệu để xuất.');
          return;
        }

        const headers = [
          { key: 'createdAt', label: 'Time' },
          { key: 'action', label: 'Action' },
          { key: 'actorUserId', label: 'Actor User ID' },
          { key: 'resourceType', label: 'Resource Type' },
          { key: 'ip', label: 'IP Address' },
        ];

        this.exportQueue.enqueueChunkedExport(
          'Audit Logs',
          totalElements,
          (page, size) => this.api.auditLogs(page, size, filters),
          headers,
          2000,
          'audit',
          filters,
        );
      },
      error: (err) => {
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  load(): void {
    this.loading = true;
    const v = this.form.getRawValue();
    const filters = {
      action: v.action || undefined,
      resourceType: v.resourceType || undefined,
      actorUserId: v.actorUsername.trim() || undefined,
      resourceId: v.accountNo.trim() || undefined,
      from: this.toIsoStart(v.from),
      to: this.toIsoEnd(v.to),
    };

    this.api.auditLogs(this.pageIndex, this.pageSize, filters).subscribe({
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

  openDetail(row: AuditLog): void {
    if (!row?.id || this.openingId) {
      return;
    }
    this.openingId = row.id;
    this.api.auditLogDetail(row.id).subscribe({
      next: (log) => {
        this.openingId = null;
        this.dialog.open(AuditDetailDialogComponent, {
          data: { log },
          width: '600px',
          maxWidth: '95vw',
        });
      },
      error: () => {
        this.openingId = null;
        // Interceptor already toasted; list row has the same fields including metadata.
        this.dialog.open(AuditDetailDialogComponent, {
          data: { log: row },
          width: '600px',
          maxWidth: '95vw',
        });
      },
    });
  }

  shortId(id: string | null | undefined): string {
    if (!id) {
      return '—';
    }
    return id.length > 8 ? `${id.slice(0, 8)}…` : id;
  }

  /** date input yyyy-mm-dd → start of day UTC ISO */
  private toIsoStart(date: string): string | undefined {
    const d = (date || '').trim();
    if (!d) {
      return undefined;
    }
    return `${d}T00:00:00.000Z`;
  }

  /** date input yyyy-mm-dd → end of day UTC ISO */
  private toIsoEnd(date: string): string | undefined {
    const d = (date || '').trim();
    if (!d) {
      return undefined;
    }
    return `${d}T23:59:59.999Z`;
  }
}
