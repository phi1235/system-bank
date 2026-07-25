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
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { map } from 'rxjs';
import { SupportTicket } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS, hasPermission } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { selectPermissions } from '../../../store/auth/auth.selectors';

/**
 * Customer IB support: list → detail (no dual-pane).
 * Create is a collapsible panel on the list view only.
 */
@Component({
  selector: 'app-customer-support',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    TranslateModule,
    PageHeaderComponent,
    LoadingComponent,
  ],
  templateUrl: './support.component.html',
  styleUrl: './support.component.scss',
})
export class CustomerSupportComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly categories = ['GENERAL', 'ACCOUNT', 'TRANSFER', 'CARD', 'KYC', 'SECURITY', 'OTHER'] as const;
  readonly priorities = ['LOW', 'NORMAL', 'HIGH'] as const;
  readonly cols = ['createdAt', 'subject', 'status', 'actions'];

  /** Create form only when admin granted ib:support:create (separate from view). */
  readonly canCreate$ = this.store
    .select(selectPermissions)
    .pipe(map((perms) => hasPermission(perms, PERMISSIONS.IB_SUPPORT_CREATE)));

  rows: SupportTicket[] = [];
  selected: SupportTicket | null = null;
  loading = false;
  detailLoading = false;
  submitting = false;
  replying = false;
  /** Collapsed by default — keep list clean. */
  showCreate = false;
  pageIndex = 0;
  pageSize = 10;
  totalElements = 0;
  replyBody = '';

  form = this.emptyForm();

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      const ticketId = params.get('ticketId')?.trim();
      if (ticketId) {
        if (this.selected?.id !== ticketId) {
          this.openDetailById(ticketId);
        }
      }
    });
    this.load();
  }

  closeDetail(): void {
    this.selected = null;
    this.replyBody = '';
    // Drop deep-link query so back stays on list
    if (this.route.snapshot.queryParamMap.get('ticketId')) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { ticketId: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
  }

  openDetailById(id: string): void {
    this.detailLoading = true;
    this.api.mySupportTicket(id).subscribe({
      next: (t) => {
        this.selected = t;
        this.replyBody = '';
        this.showCreate = false;
        this.detailLoading = false;
      },
      error: (err) => {
        this.detailLoading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  canReply(t: SupportTicket | null): boolean {
    if (!t) return false;
    const s = (t.status || '').toUpperCase();
    return s === 'OPEN' || s === 'IN_PROGRESS' || s === 'WAITING_CUSTOMER';
  }

  isClosed(t: SupportTicket | null): boolean {
    if (!t) return false;
    const s = (t.status || '').toUpperCase();
    return s === 'RESOLVED' || s === 'REJECTED';
  }

  load(): void {
    this.loading = true;
    this.api.mySupportTickets(this.pageIndex, this.pageSize).subscribe({
      next: (p) => {
        this.rows = p.items || [];
        this.totalElements = p.totalElements ?? this.rows.length;
        this.loading = false;
        if (this.selected) {
          const still = this.rows.find((r) => r.id === this.selected?.id);
          if (still && !this.selected.messages?.length) {
            // Keep richer detail payload if we already loaded messages
            this.selected = { ...still, messages: this.selected.messages };
          }
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
    this.replyBody = '';
    this.showCreate = false;
    this.detailLoading = true;
    this.api.mySupportTicket(row.id).subscribe({
      next: (t) => {
        this.selected = t;
        this.detailLoading = false;
      },
      error: (err) => {
        this.detailLoading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  toggleCreate(): void {
    this.showCreate = !this.showCreate;
  }

  submit(): void {
    const subject = this.form.subject.trim();
    const body = this.form.body.trim();
    if (!subject || !body) {
      this.toast.error(this.i18n.instant('CUSTOMER.SUPPORT_REQUIRED'));
      return;
    }
    this.submitting = true;
    this.api
      .createSupportTicket({
        category: this.form.category,
        subject,
        body,
        priority: this.form.priority,
      })
      .subscribe({
        next: (t) => {
          this.submitting = false;
          this.form = this.emptyForm();
          this.showCreate = false;
          this.toast.success(this.i18n.instant('CUSTOMER.SUPPORT_CREATE_OK'));
          this.pageIndex = 0;
          this.selected = t;
          this.load();
        },
        error: (err) => {
          this.submitting = false;
          this.toast.error(resolveHttpErrorMessage(err, this.i18n));
        },
      });
  }

  sendReply(): void {
    if (!this.selected) return;
    const body = this.replyBody.trim();
    if (!body) {
      this.toast.error(this.i18n.instant('CUSTOMER.SUPPORT_REPLY_REQUIRED'));
      return;
    }
    this.replying = true;
    this.api.postMySupportTicketMessage(this.selected.id, body).subscribe({
      next: (t) => {
        this.replying = false;
        this.replyBody = '';
        this.selected = t;
        this.toast.success(this.i18n.instant('CUSTOMER.SUPPORT_REPLY_OK'));
        this.load();
      },
      error: (err) => {
        this.replying = false;
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
      case 'WAITING_CUSTOMER':
        return 'wait';
      case 'RESOLVED':
        return 'ok';
      case 'REJECTED':
        return 'bad';
      default:
        return '';
    }
  }

  priorityClass(priority: string): string {
    switch ((priority || '').toUpperCase()) {
      case 'HIGH':
        return 'high';
      case 'LOW':
        return 'low';
      default:
        return 'normal';
    }
  }

  roleLabel(role: string | null | undefined): string {
    const r = (role || '').toUpperCase();
    if (r === 'STAFF') {
      return this.i18n.instant('CUSTOMER.SUPPORT_ROLE_STAFF');
    }
    if (r === 'CUSTOMER') {
      return this.i18n.instant('CUSTOMER.SUPPORT_ROLE_ME');
    }
    return role || '—';
  }

  roleInitial(role: string | null | undefined): string {
    const r = (role || '').toUpperCase();
    if (r === 'STAFF') return 'S';
    if (r === 'CUSTOMER') return 'B';
    return '?';
  }

  private emptyForm(): {
    category: string;
    subject: string;
    body: string;
    priority: string;
  } {
    return {
      category: 'GENERAL',
      subject: '',
      body: '',
      priority: 'NORMAL',
    };
  }
}
