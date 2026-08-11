import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { RiskBlacklistEntry, RiskRule, Transfer } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

type RuleDraft = Omit<RiskRule, 'id' | 'createdAt' | 'updatedAt'>;

@Component({
  selector: 'app-admin-risk',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatTabsModule, TranslateModule,
    PageHeaderComponent],
  templateUrl: './risk.component.html',
  styleUrl: './risk.component.scss',
})
export class AdminRiskComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);

  readonly canManage$ = this.store.select(selectHasPermission(PERMISSIONS.RISK_MANAGE));
  readonly canDecide$ = this.store.select(selectHasPermission(PERMISSIONS.RISK_DECIDE));
  readonly ruleTypes = ['AMOUNT', 'VELOCITY_COUNT', 'VELOCITY_TOTAL'];
  readonly actions = ['ALLOW', 'ALERT', 'REVIEW', 'BLOCK'];
  readonly subjectTypes = ['USER', 'ACCOUNT', 'BANK'];

  rules: RiskRule[] = [];
  blacklist: RiskBlacklistEntry[] = [];
  transfers: Transfer[] = [];
  rulePage = 0;
  ruleTotalPages = 0;
  blacklistPage = 0;
  blacklistTotalPages = 0;
  transferPage = 0;
  transferTotalPages = 0;
  loading = false;
  saving = false;
  editingRuleId: string | null = null;
  decisionNotes: Record<string, string> = {};
  rule: RuleDraft = this.emptyRule();
  blacklistDraft = { subjectType: 'USER', subjectValue: '', reason: '', expiresAt: '' };

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    this.loadRules();
    this.loadBlacklist();
    this.loadTransfers();
  }

  loadRules(): void {
    this.loading = true;
    this.api.riskRules(this.rulePage, 10).subscribe({
      next: (page) => {
        this.rules = page.items;
        this.ruleTotalPages = page.totalPages;
        this.loading = false;
      },
      error: (error) => this.fail(error),
    });
  }

  loadBlacklist(): void {
    this.api.riskBlacklist(this.blacklistPage, 10).subscribe({
      next: (page) => {
        this.blacklist = page.items;
        this.blacklistTotalPages = page.totalPages;
      },
      error: (error) => this.fail(error),
    });
  }

  loadTransfers(): void {
    this.api.adminTransfers(this.transferPage, 10, { status: 'RISK_REVIEW' }).subscribe({
      next: (page) => {
        this.transfers = page.items;
        this.transferTotalPages = page.totalPages;
      },
      error: (error) => this.fail(error),
    });
  }

  editRule(item: RiskRule): void {
    this.editingRuleId = item.id;
    this.rule = {
      code: item.code, ruleType: item.ruleType, action: item.action, enabled: item.enabled,
      priority: item.priority, thresholdAmount: item.thresholdAmount,
      windowSeconds: item.windowSeconds, maxCount: item.maxCount,
      maxTotalAmount: item.maxTotalAmount, description: item.description,
    };
  }

  cancelRule(): void {
    this.editingRuleId = null;
    this.rule = this.emptyRule();
  }

  saveRule(): void {
    if (!this.rule.code.trim() || this.saving) return;
    this.saving = true;
    const request = this.editingRuleId
      ? this.api.updateRiskRule(this.editingRuleId, this.rule)
      : this.api.createRiskRule(this.rule);
    request.subscribe({
      next: () => {
        this.saving = false;
        this.cancelRule();
        this.toast.success(this.i18n.instant('ADMIN.RISK_RULE_SAVED'));
        this.loadRules();
      },
      error: (error) => this.fail(error),
    });
  }

  addBlacklist(): void {
    if (!this.blacklistDraft.subjectValue.trim() || !this.blacklistDraft.reason.trim() || this.saving) return;
    this.saving = true;
    this.api.addRiskBlacklist({
      ...this.blacklistDraft,
      expiresAt: this.blacklistDraft.expiresAt
        ? new Date(this.blacklistDraft.expiresAt).toISOString() : null,
    }).subscribe({
      next: () => {
        this.saving = false;
        this.blacklistDraft = { subjectType: 'USER', subjectValue: '', reason: '', expiresAt: '' };
        this.toast.success(this.i18n.instant('ADMIN.RISK_BLACKLIST_ADDED'));
        this.loadBlacklist();
      },
      error: (error) => this.fail(error),
    });
  }

  deactivate(item: RiskBlacklistEntry): void {
    if (this.saving) return;
    this.saving = true;
    this.api.deactivateRiskBlacklist(item.id).subscribe({
      next: () => {
        this.saving = false;
        this.toast.success(this.i18n.instant('ADMIN.RISK_BLACKLIST_DEACTIVATED'));
        this.loadBlacklist();
      },
      error: (error) => this.fail(error),
    });
  }

  decide(item: Transfer, decision: 'approve' | 'reject'): void {
    if (this.saving) return;
    const note = (this.decisionNotes[item.transactionId] || '').trim();
    if (decision === 'reject' && !note) {
      this.toast.error(this.i18n.instant('ADMIN.RISK_NOTE_REQUIRED'));
      return;
    }
    this.saving = true;
    this.api.decideRiskTransfer(item.transactionId, decision, note).subscribe({
      next: () => {
        this.saving = false;
        delete this.decisionNotes[item.transactionId];
        this.toast.success(this.i18n.instant('ADMIN.RISK_DECISION_SAVED'));
        this.loadTransfers();
      },
      error: (error) => this.fail(error),
    });
  }

  changeRulePage(delta: number): void {
    this.rulePage += delta;
    this.loadRules();
  }

  changeBlacklistPage(delta: number): void {
    this.blacklistPage += delta;
    this.loadBlacklist();
  }

  changeTransferPage(delta: number): void {
    this.transferPage += delta;
    this.loadTransfers();
  }

  private fail(error: HttpErrorResponse): void {
    this.loading = false;
    this.saving = false;
    this.toast.error(resolveHttpErrorMessage(error, this.i18n));
  }

  private emptyRule(): RuleDraft {
    return { code: '', ruleType: 'AMOUNT', action: 'REVIEW', enabled: true, priority: 100,
      thresholdAmount: null, windowSeconds: null, maxCount: null, maxTotalAmount: null,
      description: null };
  }
}
