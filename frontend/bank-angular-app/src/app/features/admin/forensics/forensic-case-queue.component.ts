import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { Subscription, timer } from 'rxjs';
import {
  ForensicCase,
  ForensicCaseDetail,
  ForensicCaseHistory,
  ForensicCausalEdge,
  ForensicCausalGraph,
  ForensicCausalNode,
  ForensicCopilotAnswer,
  ForensicCopilotSession,
  ForensicEvidenceExport,
} from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { selectHasPermission, selectUser } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-forensic-case-queue',
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
    MatProgressSpinnerModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTabsModule,
    TranslateModule,
  ],
  templateUrl: './forensic-case-queue.component.html',
  styleUrl: './forensic-case-queue.component.scss',
})
export class ForensicCaseQueueComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);

  @Output() viewInvestigation = new EventEmitter<string>();
  @Output() runReplayForCase = new EventEmitter<{ transactionId: string; caseId: string }>();
  @Output() startCopilotForCase = new EventEmitter<{ transactionId: string | null; caseId: string; caseNumber: string }>();

  readonly canReview$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_CASE_REVIEW));
  readonly canAdmin$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_ADMIN));
  readonly canAudit$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_AUDIT_VIEW));
  readonly canExport$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_EVIDENCE_EXPORT));
  readonly statuses = ['OPEN', 'ASSIGNED', 'INVESTIGATING', 'PENDING_CHECKER', 'RESOLVED', 'DISMISSED', 'DUPLICATE', 'REOPENED'];
  readonly priorities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly resolutionCodes = ['CONFIRMED_ISSUE', 'FALSE_POSITIVE', 'EXPECTED_BEHAVIOR', 'DUPLICATE', 'DATA_GAP'];
  readonly severities = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly remediationActionTypes = ['ADJUSTMENT_JOURNAL', 'ACCOUNT_HOLD', 'MANUAL_CORRECTION', 'SYSTEM_FIX'];

  rows: ForensicCase[] = [];
  selected: ForensicCaseDetail | null = null;
  history: ForensicCaseHistory[] = [];
  currentUserId: string | null = null;
  showTechnicalDetails = false;
  selectedDetailTabIndex = 0;
  showCreatePanel = false;

  pageIndex = 0;
  pageSize = 10;
  totalElements = 0;
  loading = false;
  busy = false;
  q = '';
  status = '';
  priority = '';

  createDraft = { transactionId: '', priority: 'MEDIUM', title: '', summary: '' };
  findingDraft = { ruleCode: '', severity: 'MEDIUM', title: '', detail: '' };
  recommendation = '';
  resolutionCode = 'CONFIRMED_ISSUE';
  resolutionNote = '';
  systemicFlag = false;
  exportReason = '';
  exportJob: ForensicEvidenceExport | null = null;
  causalGraph: ForensicCausalGraph | null = null;
  causalGraphLoading = false;
  highlightedFindingId: string | null = null;
  remediationDraft = { actionType: 'ADJUSTMENT_JOURNAL', description: '', referenceId: '', completed: false };

  // Copilot Side Panel
  copilotDrawerOpen = false;
  copilotSession: ForensicCopilotSession | null = null;
  copilotAnswers: ForensicCopilotAnswer[] = [];
  copilotLoading = false;
  copilotQuestion = '';

  private pollingSub: Subscription | null = null;

  ngOnInit(): void {
    this.store.select(selectUser).subscribe((user) => (this.currentUserId = user?.userId || null));
    this.load();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  load(reset = false): void {
    if (reset) this.pageIndex = 0;
    this.loading = true;
    this.api
      .forensicCases(this.pageIndex, this.pageSize, {
        q: this.q.trim() || undefined,
        status: this.status || undefined,
        priority: this.priority || undefined,
      })
      .subscribe({
        next: (page) => {
          this.rows = page.items || [];
          this.totalElements = page.totalElements || 0;
          this.loading = false;
        },
        error: (error) => this.fail(error),
      });
  }

  page(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  create(): void {
    if (!this.createDraft.transactionId.trim() || !this.createDraft.title.trim()) return;
    this.busy = true;
    this.api
      .createForensicCase({
        transactionId: this.createDraft.transactionId.trim(),
        sourceType: 'MANUAL',
        sourceReferenceId: `MANUAL-${this.createDraft.transactionId.trim()}`,
        priority: this.createDraft.priority,
        title: this.createDraft.title.trim(),
        summary: this.createDraft.summary.trim() || undefined,
      })
      .subscribe({
        next: (item) => {
          this.busy = false;
          this.createDraft = { transactionId: '', priority: 'MEDIUM', title: '', summary: '' };
          this.showCreatePanel = false;
          this.toast.success(this.i18n.instant('FORENSICS.CASE_CREATED'));
          this.load(true);
          this.open(item);
        },
        error: (error) => this.fail(error),
      });
  }

  openById(caseId: string): void {
    this.busy = true;
    this.api.forensicCase(caseId).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.selectedDetailTabIndex = 0;
        this.causalGraph = null;
        this.exportJob = null;
        this.exportReason = '';
        this.systemicFlag = false;
        this.copilotSession = null;
        this.copilotAnswers = [];
        this.copilotDrawerOpen = false;
        this.busy = false;
        this.loadHistory(detail.forensicCase.id);
        this.checkAndStartPolling(detail.forensicCase);
        if (detail.forensicCase.transactionId) {
          this.loadCausalGraph();
        }
        this.load();
      },
      error: (error) => this.fail(error),
    });
  }

  open(item: ForensicCase): void {
    this.busy = true;
    this.api.forensicCase(item.id).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.selectedDetailTabIndex = 0;
        this.causalGraph = null;
        this.exportJob = null;
        this.exportReason = '';
        this.systemicFlag = false;
        this.copilotSession = null;
        this.copilotAnswers = [];
        this.copilotDrawerOpen = false;
        this.busy = false;
        this.loadHistory(detail.forensicCase.id);
        this.checkAndStartPolling(detail.forensicCase);
        if (detail.forensicCase.transactionId) {
          this.loadCausalGraph();
        }
      },
      error: (error) => this.fail(error),
    });
  }

  closeSelected(): void {
    this.selected = null;
    this.copilotDrawerOpen = false;
    this.stopPolling();
  }

  claim(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.currentUserId) return;
    this.command(this.api.assignForensicCase(item.id, this.currentUserId, item.version));
  }

  start(): void {
    const item = this.selected?.forensicCase;
    if (item) this.command(this.api.startForensicCase(item.id, item.version));
  }

  addFinding(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.findingDraft.ruleCode.trim() || !this.findingDraft.title.trim()) return;
    this.busy = true;
    this.api
      .addForensicFinding(item.id, {
        ruleCode: this.findingDraft.ruleCode.trim(),
        severity: this.findingDraft.severity,
        title: this.findingDraft.title.trim(),
        detail: this.findingDraft.detail.trim() || undefined,
        evidence: { source: 'MANUAL_REVIEW' },
      })
      .subscribe({
        next: () => {
          this.findingDraft = { ruleCode: '', severity: 'MEDIUM', title: '', detail: '' };
          this.refreshSelected();
        },
        error: (error) => this.fail(error),
      });
  }

  submit(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.recommendation.trim()) return;
    this.command(this.api.submitForensicCase(item.id, item.version, this.recommendation.trim()), () => {
      this.selectedDetailTabIndex = 2; // Switch to Workflow tab
    });
  }

  approve(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.resolutionNote.trim()) return;
    this.command(
      this.api.approveForensicCase(item.id, item.version, this.resolutionCode, this.resolutionNote.trim(), this.systemicFlag)
    );
  }

  reject(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.resolutionNote.trim()) return;
    this.command(this.api.rejectForensicCase(item.id, item.version, this.resolutionNote.trim()));
  }

  reopen(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.resolutionNote.trim()) return;
    this.command(this.api.reopenForensicCase(item.id, item.version, this.resolutionNote.trim()));
  }

  isMaker(item: ForensicCase): boolean {
    return !!this.currentUserId && (item.createdBy === this.currentUserId || item.submittedBy === this.currentUserId);
  }

  isAssignee(item: ForensicCase): boolean {
    return !!this.currentUserId && item.assignedTo === this.currentUserId;
  }

  goToInvestigation(): void {
    const txId = this.selected?.forensicCase?.transactionId;
    if (txId) this.viewInvestigation.emit(txId);
  }

  askCopilot(): void {
    this.openCopilotDrawer();
  }

  openCopilotDrawer(): void {
    if (!this.selected) return;
    this.copilotDrawerOpen = true;
    if (!this.copilotSession) {
      this.copilotLoading = true;
      this.api
        .createForensicCopilotSession(this.selected.forensicCase.transactionId, this.selected.forensicCase.id)
        .subscribe({
          next: (session) => {
            this.copilotSession = session;
            this.copilotLoading = false;
          },
          error: (error) => {
            this.copilotLoading = false;
            this.toast.error(resolveHttpErrorMessage(error, this.i18n));
          },
        });
    }
  }

  closeCopilotDrawer(): void {
    this.copilotDrawerOpen = false;
  }

  sendQuickPrompt(type: 'ROOT_CAUSE' | 'BALANCE_DIFF' | 'REMEDIATION'): void {
    if (!this.selected) return;
    const caseNum = this.selected.forensicCase.caseNumber;
    switch (type) {
      case 'ROOT_CAUSE':
        this.copilotQuestion = this.i18n.instant('FORENSICS.COPILOT_QUICK_ROOT_CAUSE') + ` (${caseNum})`;
        break;
      case 'BALANCE_DIFF':
        this.copilotQuestion = this.i18n.instant('FORENSICS.COPILOT_QUICK_BALANCE_DIFF') + ` (${caseNum})`;
        break;
      case 'REMEDIATION':
        this.copilotQuestion = this.i18n.instant('FORENSICS.COPILOT_QUICK_REMEDIATION') + ` (${caseNum})`;
        break;
    }
    this.askCopilotInDrawer();
  }

  askCopilotInDrawer(): void {
    const question = this.copilotQuestion.trim();
    if (!this.copilotSession || !question) return;
    this.copilotLoading = true;
    this.api.askForensicCopilot(this.copilotSession.id, question).subscribe({
      next: (answer) => {
        this.copilotAnswers = [...this.copilotAnswers, answer];
        this.copilotQuestion = '';
        this.copilotLoading = false;
      },
      error: (error) => {
        this.copilotLoading = false;
        this.toast.error(resolveHttpErrorMessage(error, this.i18n));
      },
    });
  }

  readonly stagesOrder = [
    'INITIALIZED',
    'VIOLATION_DETECTED',
    'CAUSAL_GRAPH_ATTACHED',
    'ROOT_CAUSE_CONFIRMED',
    'REPLAY_VERIFIED',
    'INVESTIGATION_CONCLUDED',
  ];

  isStageActive(stage: string): boolean {
    const current = this.selected?.forensicCase?.investigationStage || 'VIOLATION_DETECTED';
    if (stage === 'VIOLATION_DETECTED' && current === 'INITIALIZED') return true;
    return current === stage;
  }

  isStagePassed(stage: string): boolean {
    const current = this.selected?.forensicCase?.investigationStage || 'VIOLATION_DETECTED';
    const currentIndex = this.stagesOrder.indexOf(current);
    const targetIndex = this.stagesOrder.indexOf(stage);
    return currentIndex > targetIndex;
  }

  isOrchestratorAnalyzing(): boolean {
    const stage = this.selected?.forensicCase?.investigationStage;
    return stage === 'INITIALIZED' || stage === 'VIOLATION_DETECTED';
  }

  confirmRootCause(): void {
    const item = this.selected?.forensicCase;
    if (!item) return;
    this.command(this.api.confirmForensicRootCause(item.id, item.version), () => {
      this.selectedDetailTabIndex = 2; // Switch to Workflow tab
    });
  }

  verifyReplay(): void {
    const item = this.selected?.forensicCase;
    if (!item) return;
    this.command(this.api.verifyForensicReplay(item.id, item.version), () => {
      this.selectedDetailTabIndex = 2; // Switch to Workflow tab
    });
  }

  goToReplay(): void {
    const fc = this.selected?.forensicCase;
    if (fc?.transactionId) this.runReplayForCase.emit({ transactionId: fc.transactionId, caseId: fc.id });
  }

  loadCausalGraph(): void {
    const txId = this.selected?.forensicCase?.transactionId;
    if (!txId) return;
    this.causalGraphLoading = true;
    this.api.forensicCausalGraph(txId).subscribe({
      next: (graph) => {
        this.causalGraph = graph as ForensicCausalGraph;
        this.causalGraphLoading = false;
      },
      error: (error) => {
        this.causalGraphLoading = false;
        this.fail(error);
      },
    });
  }

  highlightEvidence(findingId: string): void {
    this.highlightedFindingId = this.highlightedFindingId === findingId ? null : findingId;
    if (this.highlightedFindingId && !this.causalGraph) {
      this.loadCausalGraph();
    }
  }

  addRemediation(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.remediationDraft.description.trim()) return;
    this.busy = true;
    this.api
      .recordForensicRemediation(
        item.id,
        item.version,
        this.remediationDraft.actionType,
        this.remediationDraft.description.trim(),
        this.remediationDraft.referenceId.trim() || undefined,
        this.remediationDraft.completed
      )
      .subscribe({
        next: () => {
          this.remediationDraft = { actionType: 'ADJUSTMENT_JOURNAL', description: '', referenceId: '', completed: false };
          this.toast.success(this.i18n.instant('FORENSICS.REMEDIATION_RECORDED'));
          this.refreshSelected();
        },
        error: (error) => this.fail(error),
      });
  }

  isRemediationRequired(): boolean {
    const fc = this.selected?.forensicCase;
    if (!fc) return false;
    return (
      (this.resolutionCode === 'CONFIRMED_ISSUE' || this.resolutionCode === 'DATA_GAP') &&
      fc.remediationStatus !== 'COMPLETED'
    );
  }

  get completedRemediationCount(): number {
    return this.selected?.forensicCase?.remediationActions?.filter((a) => a.completed).length || 0;
  }

  get totalRemediationCount(): number {
    return this.selected?.forensicCase?.remediationActions?.length || 0;
  }

  get remediationPercentage(): number {
    const total = this.totalRemediationCount;
    if (total === 0) return 100;
    return Math.round((this.completedRemediationCount / total) * 100);
  }

  get graphNodes(): ForensicCausalNode[] {
    return this.causalGraph?.nodes || [];
  }

  get graphEdges(): ForensicCausalEdge[] {
    return this.causalGraph?.edges || [];
  }

  getNodeIcon(type: string): string {
    switch (type?.toUpperCase()) {
      case 'TRANSFER':
        return 'swap_horiz';
      case 'SAGA':
        return 'sync_alt';
      case 'JOURNAL':
        return 'menu_book';
      case 'POSTING':
        return 'receipt_long';
      case 'OUTBOX':
        return 'outbox';
      case 'RECONCILIATION':
      case 'RECON':
        return 'balance';
      case 'AUDIT':
        return 'policy';
      default:
        return 'hub';
    }
  }

  requestExport(): void {
    const item = this.selected?.forensicCase;
    if (!item || !this.exportReason.trim()) return;
    this.busy = true;
    this.api.createForensicExport(item.id, this.exportReason.trim()).subscribe({
      next: (job) => {
        this.exportJob = job;
        this.busy = false;
        this.toast.success(this.i18n.instant('FORENSICS.EXPORT_REQUESTED'));
      },
      error: (error) => this.fail(error),
    });
  }

  refreshExport(): void {
    if (!this.exportJob) return;
    this.busy = true;
    this.api.forensicExport(this.exportJob.id).subscribe({
      next: (job) => {
        this.exportJob = job;
        this.busy = false;
      },
      error: (error) => this.fail(error),
    });
  }

  downloadExport(): void {
    if (!this.exportJob || this.exportJob.status !== 'COMPLETED') return;
    this.api.downloadForensicExport(this.exportJob.id).subscribe({
      next: (blob) => this.saveBlob(blob, `forensic-evidence-${this.exportJob?.id}.json`),
      error: (error) => this.fail(error),
    });
  }

  private checkAndStartPolling(fc: ForensicCase): void {
    this.stopPolling();
    if (fc.investigationStage === 'INITIALIZED' || fc.investigationStage === 'VIOLATION_DETECTED') {
      this.pollingSub = timer(4000, 4000).subscribe(() => {
        this.pollCase(fc.id);
      });
    }
  }

  private pollCase(caseId: string): void {
    if (!this.selected || this.selected.forensicCase.id !== caseId) {
      this.stopPolling();
      return;
    }
    this.api.forensicCase(caseId).subscribe({
      next: (detail) => {
        this.selected = detail;
        if (
          detail.forensicCase.investigationStage !== 'INITIALIZED' &&
          detail.forensicCase.investigationStage !== 'VIOLATION_DETECTED'
        ) {
          this.stopPolling();
          this.load();
        }
      },
      error: () => this.stopPolling(),
    });
  }

  private stopPolling(): void {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
      this.pollingSub = null;
    }
  }

  private command(request: ReturnType<BankApiService['startForensicCase']>, onDone?: () => void): void {
    this.busy = true;
    request.subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('FORENSICS.CASE_UPDATED'));
        this.recommendation = '';
        this.resolutionNote = '';
        this.refreshSelected();
        this.load();
        if (onDone) onDone();
      },
      error: (error) => this.fail(error),
    });
  }

  private refreshSelected(): void {
    const id = this.selected?.forensicCase.id;
    if (!id) return;
    this.api.forensicCase(id).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.busy = false;
        this.loadHistory(id);
        this.checkAndStartPolling(detail.forensicCase);
      },
      error: (error) => this.fail(error),
    });
  }

  private loadHistory(id: string): void {
    this.api.forensicCaseHistory(id).subscribe({
      next: (page) => (this.history = page.items || []),
      error: () => (this.history = []),
    });
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private fail(error: HttpErrorResponse): void {
    this.loading = false;
    this.busy = false;
    this.toast.error(resolveHttpErrorMessage(error, this.i18n));
  }
}
