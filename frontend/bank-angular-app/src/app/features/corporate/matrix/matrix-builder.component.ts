import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../../core/services/toast.service';
import { ApprovalPolicy, ApprovalTier, SimulatedPlan } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-matrix-builder',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatDialogModule,
    TranslateModule,
  ],
  templateUrl: './matrix-builder.component.html',
  styleUrl: './matrix-builder.component.scss',
})
export class MatrixBuilderComponent implements OnInit {
  private readonly api = inject(CorporateApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  corporateId = '';
  activePolicy: ApprovalPolicy | null = null;
  policies: ApprovalPolicy[] = [];

  simulateAmount = 50000000;
  simulatedPlan: SimulatedPlan | null = null;

  newPolicy: {
    policyName: string;
    allowSelfApproval: boolean;
    requireRoleSeparation: boolean;
    currency: string;
    tiers: ApprovalTier[];
  } = {
    policyName: '',
    allowSelfApproval: false,
    requireRoleSeparation: true,
    currency: 'VND',
    tiers: [],
  };

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (this.corporateId) {
      this.loadPolicies();
    }
  }

  loadPolicies() {
    this.api.getActivePolicy(this.corporateId).subscribe({
      next: (p) => {
        this.activePolicy = p;
        this.runSimulation();
      },
      error: () => (this.activePolicy = null),
    });

    this.api.getPolicies(this.corporateId).subscribe({
      next: (list) => (this.policies = list),
    });
  }

  runSimulation() {
    if (!this.simulateAmount || this.simulateAmount <= 0) return;
    this.api.simulatePlan(this.corporateId, this.simulateAmount).subscribe({
      next: (plan) => (this.simulatedPlan = plan),
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.SIMULATION_ERROR')),
    });
  }

  activatePolicy(policyId: string) {
    this.api.activatePolicy(this.corporateId, policyId).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('CORPORATE.POLICY_ACTIVATE_SUCCESS'));
        this.loadPolicies();
      },
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.POLICY_ACTIVATE_ERROR')),
    });
  }

  openCreatePolicyModal() {
    this.newPolicy = {
      policyName: this.translate.instant('CORPORATE.DEFAULT_POLICY_NAME'),
      allowSelfApproval: false,
      requireRoleSeparation: true,
      currency: 'VND',
      tiers: [
        {
          tierName: this.translate.instant('CORPORATE.DEFAULT_TIER_LOW'),
          minAmount: 0,
          maxAmount: 100000000,
          priorityOrder: 1,
          steps: [
            {
              stepOrder: 1,
              stepName: this.translate.instant('CORPORATE.DEFAULT_CHECKER_STEP'),
              requiredRole: 'CHECKER',
              minApprovals: 1,
              authMethod: 'STANDARD',
            },
          ],
        },
        {
          tierName: this.translate.instant('CORPORATE.DEFAULT_TIER_HIGH'),
          minAmount: 100000000,
          priorityOrder: 2,
          steps: [
            {
              stepOrder: 1,
              stepName: this.translate.instant('CORPORATE.DEFAULT_CHECKER_CONFIRM_STEP'),
              requiredRole: 'CHECKER',
              minApprovals: 1,
              authMethod: 'STANDARD',
            },
            {
              stepOrder: 2,
              stepName: this.translate.instant('CORPORATE.DEFAULT_CFO_STEP'),
              requiredRole: 'CFO',
              minApprovals: 1,
              authMethod: 'TOTP_STEPUP',
            },
          ],
        },
      ],
    };
  }

  addTier() {
    const previous = this.newPolicy.tiers.at(-1);
    let minAmount = 0;
    if (previous) {
      if (previous.maxAmount == null) {
        previous.maxAmount = previous.minAmount + 100000000;
      }
      minAmount = previous.maxAmount;
    }
    this.newPolicy.tiers.push({
      tierName: this.translate.instant('CORPORATE.DEFAULT_TIER_NUMBER', {
        number: this.newPolicy.tiers.length + 1,
      }),
      minAmount,
      priorityOrder: this.newPolicy.tiers.length + 1,
      steps: [
        {
          stepOrder: 1,
          stepName: this.translate.instant('CORPORATE.DEFAULT_CHECKER_STEP'),
          requiredRole: 'CHECKER',
          minApprovals: 1,
          authMethod: 'STANDARD',
        },
      ],
    });
  }

  removeTier(index: number) {
    this.newPolicy.tiers.splice(index, 1);
    this.normalizeTiers();
  }

  addStep(tier: ApprovalTier) {
    tier.steps.push({
      stepOrder: tier.steps.length + 1,
      stepName: this.translate.instant('CORPORATE.DEFAULT_APPROVAL_STEP', {
        number: tier.steps.length + 1,
      }),
      requiredRole: 'CFO',
      minApprovals: 1,
      authMethod: 'STANDARD',
    });
  }

  removeStep(tier: ApprovalTier, stepIndex: number) {
    tier.steps.splice(stepIndex, 1);
    tier.steps.forEach((s, idx) => (s.stepOrder = idx + 1));
  }

  private normalizeTiers() {
    this.newPolicy.tiers.forEach((tier, index) => {
      tier.priorityOrder = index + 1;
      if (index === 0) {
        tier.minAmount = 0;
      } else {
        const previous = this.newPolicy.tiers[index - 1];
        if (previous.maxAmount == null) {
          previous.maxAmount = tier.minAmount;
        }
        tier.minAmount = previous.maxAmount;
      }
    });
    const last = this.newPolicy.tiers.at(-1);
    if (last) {
      last.maxAmount = undefined;
    }
  }

  savePolicy() {
    this.api.createPolicy(this.corporateId, this.newPolicy).subscribe({
      next: (created) => {
        this.toast.success(this.translate.instant('CORPORATE.POLICY_CREATE_SUCCESS', { version: created.versionNumber }));
        this.loadPolicies();
      },
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.POLICY_CREATE_ERROR')),
    });
  }
}
