import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { BankItem, SettlementPreview, SplitLegItem, SplitRule } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-split-rules',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDividerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './business-split-rules.component.html',
  styleUrl: './business-split-rules.component.scss',
})
export class BusinessSplitRulesComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  splitRules: SplitRule[] = [];
  banks: BankItem[] = [];
  bankMap = new Map<string, BankItem>();
  loading = false;

  // Create Split Rule Modal
  showCreateModal = false;
  newRuleName = '';
  newRuleItems: SplitLegItem[] = [];
  creatingRule = false;

  get canManageSplit(): boolean {
    return this.businessContext.hasPermission('split:manage') || this.businessContext.hasPermission('split:create');
  }

  // Split Preview Tool
  previewGrossAmount = 1000000;
  previewSelectedRuleId = '';
  previewResult: SettlementPreview | null = null;
  calculatingPreview = false;

  ngOnInit(): void {
    this.loadBanks();
    this.businessContext.selectedOrg$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadRules();
    });
  }

  loadBanks(): void {
    this.api.listBanks().subscribe({
      next: (res) => {
        this.banks = res || [];
        this.bankMap.clear();
        this.banks.forEach((b) => {
          if (b.bin) this.bankMap.set(b.bin, b);
          if (b.bankCode) this.bankMap.set(b.bankCode, b);
        });
      },
    });
  }

  getBank(binOrCode?: string): BankItem | undefined {
    if (!binOrCode) return undefined;
    return this.bankMap.get(binOrCode);
  }

  getBankName(binOrCode?: string): string {
    if (!binOrCode) return '';
    const b = this.getBank(binOrCode);
    return b ? b.shortName : binOrCode;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadRules(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.loading = true;
    this.api.listSplitRules(orgId).subscribe({
      next: (rules) => {
        this.splitRules = rules || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  openCreateModal(): void {
    this.newRuleName = '';
    this.newRuleItems = [
      {
        beneficiaryType: 'PLATFORM',
        beneficiaryName: 'Platform Commission',
        splitType: 'PERCENTAGE',
        value: 5,
        priority: 1,
      },
      {
        beneficiaryType: 'SELLER_INTERNAL',
        beneficiaryName: 'Merchant Net Account',
        splitType: 'REMAINDER',
        value: 0,
        priority: 2,
      },
    ];
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  addLeg(): void {
    this.newRuleItems.push({
      beneficiaryType: 'SELLER_EXTERNAL',
      beneficiaryName: 'Partner Seller',
      bankBin: '970422',
      accountNumber: '',
      splitType: 'PERCENTAGE',
      value: 10,
      priority: this.newRuleItems.length + 1,
    });
  }

  removeLeg(index: number): void {
    this.newRuleItems.splice(index, 1);
  }

  submitCreateRule(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!this.newRuleName.trim() || this.newRuleItems.length === 0) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }

    this.creatingRule = true;
    this.api
      .createSplitRule(orgId, {
        name: this.newRuleName.trim(),
        items: this.newRuleItems,
      })
      .subscribe({
        next: () => {
          this.creatingRule = false;
          this.showCreateModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.loadRules();
        },
        error: (err) => {
          this.creatingRule = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  deleteRule(rule: SplitRule): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!confirm(this.i18n.instant('BUSINESS.SPLIT.DELETE_CONFIRM'))) return;

    this.api.deleteSplitRule(orgId, rule.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadRules();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }

  calculatePreview(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId || !this.previewGrossAmount || this.previewGrossAmount <= 0) return;

    this.calculatingPreview = true;
    this.api
      .previewSettlement(orgId, {
        grossAmount: this.previewGrossAmount,
        splitRuleId: this.previewSelectedRuleId || undefined,
      })
      .subscribe({
        next: (res) => {
          this.previewResult = res;
          this.calculatingPreview = false;
        },
        error: (err) => {
          this.calculatingPreview = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }
}
