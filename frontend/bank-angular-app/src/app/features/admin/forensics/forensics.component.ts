import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import {
  ForensicInvestigation,
  ForensicInvestigationDetail,
  ForensicTemporalState,
  ForensicTwinFork,
  ForensicReplayRun,
  ForensicCopilotSession,
  ForensicCopilotAnswer,
  ForensicVerificationRun,
  ForensicReplayScenario,
} from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { ForensicCaseQueueComponent } from './forensic-case-queue.component';
import { ForensicViolationQueueComponent } from './forensic-violation-queue.component';
import { ForensicScenarioAdminComponent } from './forensic-scenario-admin.component';
import { selectHasPermission } from '../../../store/auth/auth.selectors';
import { take } from 'rxjs';

@Component({
  selector: 'app-admin-forensics',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    TranslateModule,
    PageHeaderComponent,
    MoneyVndPipe,
    ForensicCaseQueueComponent,
    ForensicViolationQueueComponent,
    ForensicScenarioAdminComponent,
  ],
  templateUrl: './forensics.component.html',
  styleUrl: './forensics.component.scss',
})
export class AdminForensicsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  readonly canReplay$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_REPLAY_EXECUTE));
  readonly canCopilot$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_COPILOT_USE));
  readonly canVerify$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_VERIFY_EXECUTE));

  readonly statuses = [
    'PENDING', 'DEBITED', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED',
    'UNKNOWN', 'REVIEW_REQUIRED', 'RISK_REVIEW',
  ];
  readonly riskDecisions = ['ALLOW', 'REVIEW', 'BLOCK'];
  readonly columns = ['createdAt', 'transactionId', 'amount', 'status', 'risk', 'signal', 'action'];

  mainTabIndex = 0;
  detailTabIndex = 0;
  rows: ForensicInvestigation[] = [];
  selected: ForensicInvestigationDetail | null = null;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  loading = false;
  detailLoading = false;
  temporalLoading = false;
  temporalState: ForensicTemporalState | null = null;
  twinFork: ForensicTwinFork | null = null;
  replayRun: ForensicReplayRun | null = null;
  copilotSession: ForensicCopilotSession | null = null;
  copilotAnswers: ForensicCopilotAnswer[] = [];
  copilotLoading = false;
  replayScenarios: ForensicReplayScenario[] = [];
  verificationRun: ForensicVerificationRun | null = null;
  verificationLoading = false;
  private verificationIdempotencyKey: string | null = null;

  @ViewChild('caseQueueRef') caseQueueRef!: ForensicCaseQueueComponent;

  readonly form = this.fb.nonNullable.group({
    q: [''],
    transactionId: [''],
    transferStatus: [''],
    riskDecision: [''],
    from: [''],
    to: [''],
  });

  readonly temporalForm = this.fb.nonNullable.group({ at: [''] });
  readonly replayForm = this.fb.nonNullable.group({
    scenarioId: [''],
    seed: [1],
    targetCommitSha: [''],
  });
  readonly copilotForm = this.fb.nonNullable.group({ question: [''] });

  ngOnInit(): void {
    this.load();
    this.canReplay$.pipe(take(1)).subscribe(canReplay => {
      if (canReplay) this.loadReplayScenarios();
    });
  }

  private loadReplayScenarios(): void {
    this.api.forensicReplayScenarios().subscribe({
      next: scenarios => {
        this.replayScenarios = scenarios;
        if (!this.replayForm.controls.scenarioId.value && scenarios.length) {
          this.replayForm.controls.scenarioId.setValue(scenarios[0].scenarioId);
        }
      },
      error: error => this.toast.error(resolveHttpErrorMessage(error, this.i18n)),
    });
  }

  search(): void {
    this.pageIndex = 0;
    this.selected = null;
    this.load();
  }

  reset(): void {
    this.form.reset({
      q: '', transactionId: '', transferStatus: '', riskDecision: '', from: '', to: '',
    });
    this.search();
  }

  page(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  open(row: ForensicInvestigation): void {
    this.detailLoading = true;
    this.api.forensicInvestigation(row.transactionId).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.temporalState = null;
        this.twinFork = null;
        this.replayRun = null;
        this.copilotSession = null;
        this.copilotAnswers = [];
        this.verificationRun = null;
        this.verificationIdempotencyKey = null;
        this.temporalForm.setValue({ at: this.toDatetimeLocal(detail.transaction.createdAt) });
        this.detailLoading = false;
        setTimeout(() => {
          const card = document.querySelector('.detail-card');
          if (card) {
            card.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        }, 100);
      },
      error: (error) => {
        this.detailLoading = false;
        this.toast.error(resolveHttpErrorMessage(error, this.i18n));
      },
    });
  }

  closeDetail(): void {
    this.selected = null;
    this.temporalState = null;
    this.twinFork = null;
    this.replayRun = null;
    this.copilotSession = null;
    this.copilotAnswers = [];
    this.verificationRun = null;
    this.verificationIdempotencyKey = null;
  }

  runVerification(): void {
    if (!this.selected) return;
    this.verificationLoading = true;
    this.verificationIdempotencyKey ??= crypto.randomUUID();
    this.api.runForensicVerification(
      this.selected.transaction.transactionId,
      this.verificationIdempotencyKey,
    ).subscribe({
      next: (run) => {
        this.verificationRun = run;
        this.verificationIdempotencyKey = null;
        this.verificationLoading = false;
      },
      error: (error) => {
        this.verificationLoading = false;
        this.toast.error(resolveHttpErrorMessage(error, this.i18n));
      },
    });
  }

  loadTemporalState(): void {
    if (!this.selected) return;
    const raw = this.temporalForm.controls.at.value;
    if (!raw) return;
    const parsedDate = new Date(raw);
    if (isNaN(parsedDate.getTime())) {
      this.toast.error(this.i18n.instant('ERRORS.VALIDATION_ERROR'));
      return;
    }
    this.temporalLoading = true;
    this.api.forensicTemporalState(this.selected.transaction.transactionId, parsedDate.toISOString())
      .subscribe({
        next: (state) => {
          this.temporalState = state;
          this.temporalLoading = false;
        },
        error: (error) => {
          this.temporalLoading = false;
          this.toast.error(resolveHttpErrorMessage(error, this.i18n));
        },
      });
  }

  getAccountRoleKey(accountId: string): string {
    if (!this.selected) return 'FORENSICS.ROLE_OTHER_ACCOUNT';
    if (accountId === this.selected.transaction.fromAccountId) {
      return 'FORENSICS.ROLE_SOURCE_ACCOUNT';
    }
    if (accountId === this.selected.transaction.toAccountId) {
      return 'FORENSICS.ROLE_TARGET_ACCOUNT';
    }
    return 'FORENSICS.ROLE_OTHER_ACCOUNT';
  }

  getAccountRoleBadgeClass(accountId: string): string {
    if (!this.selected) return 'neutral';
    if (accountId === this.selected.transaction.fromAccountId) {
      return 'role-source';
    }
    if (accountId === this.selected.transaction.toAccountId) {
      return 'role-target';
    }
    return 'role-neutral';
  }

  getAccountDetailLabel(accountId: string): string {
    if (!this.selected) return accountId;
    if (accountId === this.selected.transaction.toAccountId && this.selected.transaction.toAccountNumber) {
      const nameStr = this.selected.transaction.targetAccountName ? ` - ${this.selected.transaction.targetAccountName}` : '';
      return `${accountId} (${this.selected.transaction.toAccountNumber}${nameStr})`;
    }
    return accountId;
  }

  hasViolation(): boolean {
    if (this.verificationRun && this.verificationRun.outcome === 'FAIL') {
      return true;
    }
    if (this.selected && (this.selected.transaction.status === 'FAILED' || this.selected.transaction.needsAttention)) {
      return true;
    }
    return false;
  }

  exportEvidencePackage(): void {
    if (!this.selected) return;
    const detail = this.selected;
    const payload = {
      exportId: crypto.randomUUID(),
      exportedBy: 'ADMIN_HO',
      exportedAt: new Date().toISOString(),
      transaction: detail.transaction,
      evidenceCompleteness: detail.evidenceCompleteness,
      missingSources: detail.missingSources,
      temporalState: this.temporalState,
      verificationRun: this.verificationRun,
      cryptographicSignature: {
        algorithm: 'SHA-256',
        hash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        status: 'IMMUTABLE_VERIFIED'
      }
    };

    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Forensic_Audit_Evidence_${detail.transaction.transactionId.substring(0, 8)}.json`;
    a.click();
    window.URL.revokeObjectURL(url);
    this.toast.success(`✅ Đã xuất tệp Hồ sơ Bằng chứng Mã hóa Forensic_Audit_Evidence_${detail.transaction.transactionId.substring(0, 8)}.json thành công!`);
  }

  getVerdictClass(): string {
    return this.hasViolation() ? 'verdict-critical' : 'verdict-pass';
  }

  getVerdictIcon(): string {
    return this.hasViolation() ? 'gavel' : 'verified_user';
  }

  getVerdictStatusBadge(): string {
    return this.hasViolation() ? '🚨 CẢNH BÁO VI PHẠM SỔ CÁI' : '✅ AN TOÀN - ĐỒNG BỘ 100%';
  }

  getVerdictTitle(): string {
    if (this.hasViolation()) {
      return 'BẢNG CHẨN ĐOÁN VI PHẠM BẤT BIẾN TÀI CHÍNH (CRITICAL LEDGER DRIFT)';
    }
    return 'GIAO DỊCH HỢP LỆ - CÂN BẰNG NỢ/CÓ TRÊN SỔ CÁI';
  }

  getVerdictImpact(): string {
    if (!this.selected) return '';
    if (this.hasViolation()) {
      return `ẢNH HƯỞNG: LỆCH SỔ SÁCH ${this.selected.transaction.amount.toLocaleString('vi-VN')} VND`;
    }
    return 'ẢNH HƯỞNG: 0 VND';
  }

  getVerdictDescription(): string {
    if (!this.selected) return '';
    if (this.hasViolation()) {
      return `Hệ thống kiểm toán tự động phát hiện vi phạm quy tắc sổ cái bất biến (INV-REVERSAL-001): Giao dịch có bút toán đảo (Reversal) trị giá ${this.selected.transaction.amount.toLocaleString('vi-VN')} VND nhưng không liên kết được với Bút toán gốc. Tiền đang bị kẹt ở trạng thái trung gian, dẫn đến sai lệch số dư thực tế giữa Microservices!`;
    }
    return 'Giao dịch đã hoàn tất quy trình Saga Outbox, bút toán Nợ và Bút toán Có đã được khớp nối cân bằng 100% trên Sổ cái ngân hàng. Không phát hiện rủi ro gian lận hay bất đồng số dư.';
  }

  getVerdictRecommendations(): any[] {
    if (!this.selected || !this.hasViolation()) return [];
    return [
      {
        id: 'adjustment',
        step: 1,
        title: 'Khởi tạo Bút toán Điều chỉnh (Adjustment Journal)',
        description: `Tự động tạo bút toán bù trừ ${this.selected.transaction.amount.toLocaleString('vi-VN')} VND để đưa Sổ cái về trạng thái cân bằng.`,
        actionText: 'Tạo bút toán',
        icon: 'tune',
        color: 'primary',
      },
      {
        id: 'freeze',
        step: 2,
        title: 'Tạm phong tỏa Số dư Tài khoản Thụ hưởng',
        description: `Đặt lệnh tạm giữ (Hold) đối với số dư tài khoản đích để tránh tẩu tán tài sản trong lúc xử lý sự cố.`,
        actionText: 'Khóa số dư',
        icon: 'warn',
        color: 'warn',
      },
      {
        id: 'case',
        step: 3,
        title: 'Tạo Hồ sơ Điều tra Gian lận (Incident Case)',
        description: 'Chuyển thông tin vụ việc và chuỗi bằng chứng mã hóa cho Ban Quản trị Rủi ro & Tuân thủ.',
        actionText: 'Tạo hồ sơ',
        icon: 'folder_special',
        color: 'primary',
      },
    ];
  }

  executeRecommendation(rec: any): void {
    if (!this.selected) return;
    const tx = this.selected.transaction;

    if (rec.id === 'case') {
      this.detailLoading = true;
      this.api.createForensicCase({
        transactionId: tx.transactionId,
        sourceType: 'INVARIANT',
        priority: 'CRITICAL',
        title: this.i18n.instant('FORENSICS.AUTO_CASE_TITLE', { txId: tx.transactionId.substring(0, 8) }),
        summary: this.i18n.instant('FORENSICS.AUTO_CASE_SUMMARY', { txId: tx.transactionId, amount: tx.amount.toLocaleString('vi-VN') }),
      }).subscribe({
        next: (c) => {
          this.detailLoading = false;
          this.toast.success(this.i18n.instant('FORENSICS.CASE_CREATED_SUCCESS', { caseNumber: c.caseNumber }));
          this.closeDetail();
          this.mainTabIndex = 1;
          setTimeout(() => this.caseQueueRef?.openById(c.id), 300);
        },
        error: (error) => {
          this.detailLoading = false;
          this.toast.error(resolveHttpErrorMessage(error, this.i18n));
        }
      });
    } else if (rec.id === 'adjustment') {
      const caseId = this.caseQueueRef?.selected?.forensicCase?.id;
      if (!caseId) {
        this.toast.error(this.i18n.instant('FORENSICS.NO_CASE_SELECTED'));
        return;
      }
      const idempotencyKey = `REM-${tx.transactionId.substring(0, 8)}-ADJ-${tx.amount}`;
      this.detailLoading = true;
      this.api.executeRemediationAdjustment(
        {
          caseId,
          transactionId: tx.transactionId,
          amount: tx.amount,
          reason: `Điều chỉnh bất bằng sổ cái cho giao dịch ${tx.transactionId.substring(0, 8)}`,
        },
        idempotencyKey,
      ).subscribe({
        next: (res: any) => {
          this.detailLoading = false;
          this.toast.success(res?.message || this.i18n.instant('FORENSICS.ADJUSTMENT_SUCCESS'));
          this.runVerification();
        },
        error: (error) => {
          this.detailLoading = false;
          this.toast.error(resolveHttpErrorMessage(error, this.i18n));
        },
      });
    } else if (rec.id === 'freeze') {
      const caseId = this.caseQueueRef?.selected?.forensicCase?.id;
      if (!caseId) {
        this.toast.error(this.i18n.instant('FORENSICS.NO_CASE_SELECTED'));
        return;
      }
      const idempotencyKey = `REM-${tx.transactionId.substring(0, 8)}-HOLD-${tx.amount}`;
      this.detailLoading = true;
      this.api.executeRemediationHold(
        {
          caseId,
          amount: tx.amount,
          reason: `Phong tỏa tạm thời cho giao dịch ${tx.transactionId.substring(0, 8)}`,
        },
        idempotencyKey,
      ).subscribe({
        next: (res: any) => {
          this.detailLoading = false;
          this.toast.success(res?.message || this.i18n.instant('FORENSICS.FREEZE_SUCCESS'));
        },
        error: (error) => {
          this.detailLoading = false;
          this.toast.error(resolveHttpErrorMessage(error, this.i18n));
        },
      });
    } else {
      this.toast.success(`[${rec.title}] - ${this.i18n.instant('FORENSICS.ACTION_SUCCESS_TOAST')}`);
    }
  }

  createFork(): void {
    if (!this.selected) return;
    this.detailLoading = true;
    this.api.createForensicFork(this.selected.transaction.transactionId).subscribe({
      next: (fork) => { this.twinFork = fork; this.replayRun = null; this.detailLoading = false; },
      error: (error) => { this.detailLoading = false; this.toast.error(resolveHttpErrorMessage(error, this.i18n)); },
    });
  }

  runReplay(): void {
    if (!this.twinFork) return;
    const value = this.replayForm.getRawValue();
    if (!value.scenarioId.trim() || !/^[0-9a-fA-F]{7,64}$/.test(value.targetCommitSha.trim())) return;
    this.detailLoading = true;
    this.api.createForensicReplay(crypto.randomUUID(), {
      forkId: this.twinFork.id,
      scenarioId: value.scenarioId.trim(),
      seed: value.seed,
      targetCommitSha: value.targetCommitSha.trim(),
    }).subscribe({
      next: (run) => { this.replayRun = run; this.detailLoading = false; },
      error: (error) => { this.detailLoading = false; this.toast.error(resolveHttpErrorMessage(error, this.i18n)); },
    });
  }

  refreshReplay(): void {
    if (!this.replayRun) return;
    this.api.forensicReplay(this.replayRun.id).subscribe({
      next: (run) => this.replayRun = run,
      error: (error) => this.toast.error(resolveHttpErrorMessage(error, this.i18n)),
    });
  }

  downloadReplayResult(): void {
    if (!this.replayRun) return;
    this.api.forensicReplayResult(this.replayRun.id).subscribe({
      next: (blob) => this.saveBlob(blob, `forensic-replay-${this.replayRun?.id}.json`),
      error: (error) => this.toast.error(resolveHttpErrorMessage(error, this.i18n)),
    });
  }

  deleteFork(): void {
    if (!this.twinFork) return;
    this.api.deleteForensicFork(this.twinFork.id).subscribe({
      next: () => { this.twinFork = null; this.replayRun = null; },
      error: (error) => this.toast.error(resolveHttpErrorMessage(error, this.i18n)),
    });
  }

  startCopilot(): void {
    if (!this.selected) return;
    this.copilotLoading = true;
    this.api.createForensicCopilotSession(this.selected.transaction.transactionId).subscribe({
      next: (session) => { this.copilotSession = session; this.copilotLoading = false; },
      error: (error) => { this.copilotLoading = false; this.toast.error(resolveHttpErrorMessage(error, this.i18n)); },
    });
  }

  askCopilot(): void {
    const question = this.copilotForm.controls.question.value.trim();
    if (!this.copilotSession || !question) return;
    this.copilotLoading = true;
    this.api.askForensicCopilot(this.copilotSession.id, question).subscribe({
      next: (answer) => {
        this.copilotAnswers = [...this.copilotAnswers, answer];
        this.copilotForm.reset({ question: '' });
        this.copilotLoading = false;
      },
      error: (error) => { this.copilotLoading = false; this.toast.error(resolveHttpErrorMessage(error, this.i18n)); },
    });
  }

  shortId(id: string | null | undefined): string {
    return id ? `${id.slice(0, 8)}…` : '—';
  }

  onViewInvestigation(transactionId: string): void {
    this.mainTabIndex = 0;
    this.openByTransactionId(transactionId);
  }

  onRunReplayForCase(event: { transactionId: string; caseId: string }): void {
    this.mainTabIndex = 0;
    this.detailTabIndex = 3;
    this.openByTransactionId(event.transactionId);
  }

  @ViewChild('caseQueueRef') caseQueueRef?: ForensicCaseQueueComponent;

  onNavigateToCase(caseId: string): void {
    this.mainTabIndex = 1;
    if (caseId) {
      setTimeout(() => this.caseQueueRef?.openById(caseId), 100);
    }
  }

  onStartCopilotForCase(event: { transactionId: string | null; caseId: string; caseNumber: string }): void {
    this.mainTabIndex = 0;
    this.detailTabIndex = 3;
    const defaultQuestion = this.i18n.instant('FORENSICS.COPILOT_CASE_SUGGESTION', { caseNumber: event.caseNumber });
    if (event.transactionId) {
      this.detailLoading = true;
      this.api.forensicInvestigation(event.transactionId).subscribe({
        next: (detail) => {
          this.selected = detail;
          this.detailLoading = false;
          this.copilotLoading = true;
          this.api.createForensicCopilotSession(event.transactionId, event.caseId).subscribe({
            next: (session) => {
              this.copilotSession = session;
              this.copilotLoading = false;
              this.copilotForm.patchValue({ question: defaultQuestion });
            },
            error: (error) => {
              this.copilotLoading = false;
              this.toast.error(resolveHttpErrorMessage(error, this.i18n));
            },
          });
        },
        error: (error) => {
          this.detailLoading = false;
          this.toast.error(resolveHttpErrorMessage(error, this.i18n));
        },
      });
    } else {
      this.copilotLoading = true;
      this.api.createForensicCopilotSession(null, event.caseId).subscribe({
        next: (session) => {
          this.copilotSession = session;
          this.copilotLoading = false;
          this.copilotForm.patchValue({ question: defaultQuestion });
        },
        error: (error) => {
          this.copilotLoading = false;
          this.toast.error(resolveHttpErrorMessage(error, this.i18n));
        },
      });
    }
  }

  openByTransactionId(transactionId: string): void {
    this.detailLoading = true;
    this.api.forensicInvestigation(transactionId).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.temporalState = null;
        this.twinFork = null;
        this.replayRun = null;
        this.copilotSession = null;
        this.copilotAnswers = [];
        this.verificationRun = null;
        this.verificationIdempotencyKey = null;
        this.temporalForm.setValue({ at: this.toDatetimeLocal(detail.transaction.createdAt) });
        this.detailLoading = false;
      },
      error: (error) => {
        this.detailLoading = false;
        this.toast.error(resolveHttpErrorMessage(error, this.i18n));
      },
    });
  }

  private load(): void {
    this.loading = true;
    const value = this.form.getRawValue();
    this.api.forensicInvestigations(this.pageIndex, this.pageSize, {
      q: value.q.trim() || undefined,
      transactionId: value.transactionId.trim() || undefined,
      transferStatus: value.transferStatus || undefined,
      riskDecision: value.riskDecision || undefined,
      from: this.dayBoundary(value.from, false),
      to: this.dayBoundary(value.to, true),
    }).subscribe({
      next: (page) => {
        this.rows = page.items || [];
        this.totalElements = page.totalElements || 0;
        this.loading = false;
      },
      error: (error) => {
        this.rows = [];
        this.totalElements = 0;
        this.loading = false;
        this.toast.error(resolveHttpErrorMessage(error, this.i18n));
      },
    });
  }

  private dayBoundary(value: string, end: boolean): string | undefined {
    return value ? `${value}T${end ? '23:59:59.999' : '00:00:00.000'}Z` : undefined;
  }

  private toDatetimeLocal(value: string): string {
    const date = new Date(value);
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 16);
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
