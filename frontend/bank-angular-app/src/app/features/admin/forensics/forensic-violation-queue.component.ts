import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { ForensicFinding } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-forensic-violation-queue',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatPaginatorModule, MatSelectModule, TranslateModule],
  templateUrl: './forensic-violation-queue.component.html',
  styleUrl: './forensic-violation-queue.component.scss',
})
export class ForensicViolationQueueComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  readonly canReview$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_CASE_REVIEW));
  readonly dispositions = ['', 'UNREVIEWED', 'ACKNOWLEDGED', 'RESOLVED'];
  readonly severities = ['', 'INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  rows: ForensicFinding[] = [];
  selected: ForensicFinding | null = null;
  disposition = 'UNREVIEWED';
  severity = '';
  pageIndex = 0;
  pageSize = 10;
  totalElements = 0;
  busy = false;
  note = '';
  reason = '';
  evidence = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.forensicViolations(this.pageIndex, this.pageSize, {
      disposition: this.disposition || undefined,
      severity: this.severity || undefined,
    }).subscribe({
      next: page => { this.rows = page.items; this.totalElements = page.totalElements; },
      error: error => this.toast.error(resolveHttpErrorMessage(error, this.i18n)),
    });
  }

  page(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  choose(item: ForensicFinding): void { this.selected = item; this.note = ''; this.reason = ''; this.evidence = ''; }

  acknowledge(): void {
    if (!this.selected || !this.note.trim()) return;
    this.busy = true;
    this.api.acknowledgeForensicViolation(this.selected.id, this.selected.version, this.note).subscribe({
      next: item => { this.selected = item; this.busy = false; this.load(); },
      error: error => { this.busy = false; this.toast.error(resolveHttpErrorMessage(error, this.i18n)); },
    });
  }

  resolve(): void {
    if (!this.selected || !this.reason.trim()) return;
    let structured: Record<string, unknown> = {};
    try { structured = this.evidence.trim() ? JSON.parse(this.evidence) : {}; }
    catch { this.toast.error(this.i18n.instant('FORENSICS.INVALID_EVIDENCE_JSON')); return; }
    this.busy = true;
    this.api.resolveForensicViolation(this.selected.id, this.selected.version, this.reason, structured).subscribe({
      next: item => {
        this.selected = item;
        this.busy = false;
        this.toast.success(this.i18n.instant('FORENSICS.HO_FINDING_CLOSED'));
        this.load();
      },
      error: error => { this.busy = false; this.toast.error(resolveHttpErrorMessage(error, this.i18n)); },
    });
  }

  resolveDirect(): void {
    if (!this.selected) return;
    const finalReason = this.reason.trim() || this.i18n.instant('FORENSICS.HO_FINDING_CLOSED');
    const structured = {
      closedBy: 'ADMIN_HO',
      timestamp: new Date().toISOString(),
      disposition: 'RESOLVED_BY_HO'
    };
    this.busy = true;
    this.api.resolveForensicViolation(this.selected.id, this.selected.version, finalReason, structured).subscribe({
      next: item => {
        this.selected = item;
        this.busy = false;
        this.toast.success(this.i18n.instant('FORENSICS.HO_FINDING_CLOSED'));
        this.load();
      },
      error: error => {
        this.busy = false;
        this.toast.error(resolveHttpErrorMessage(error, this.i18n));
      },
    });
  }
}
