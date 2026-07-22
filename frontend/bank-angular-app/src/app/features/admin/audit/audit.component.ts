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
  cols = ['createdAt', 'action', 'actorUserId', 'resourceType', 'resourceId', 'ip', 'actions'];

  /** Known actions in this service (extensible free-text still allowed via Other path). */
  readonly actionOptions = ['', 'TRANSFER_CREATE'];
  readonly resourceTypeOptions = ['', 'TRANSFER'];

  form = this.fb.nonNullable.group({
    action: [''],
    resourceType: [''],
    actorUserId: [''],
    resourceId: [''],
    from: [''],
    to: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  get hasActiveFilters(): boolean {
    const v = this.form.getRawValue();
    return !!(v.action || v.resourceType || v.actorUserId.trim() || v.resourceId.trim() || v.from || v.to);
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.form.reset({
      action: '',
      resourceType: '',
      actorUserId: '',
      resourceId: '',
      from: '',
      to: '',
    });
    this.pageIndex = 0;
    this.load();
  }

  load(): void {
    this.loading = true;
    const v = this.form.getRawValue();
    const filters = {
      action: v.action || undefined,
      resourceType: v.resourceType || undefined,
      actorUserId: v.actorUserId.trim() || undefined,
      resourceId: v.resourceId.trim() || undefined,
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
