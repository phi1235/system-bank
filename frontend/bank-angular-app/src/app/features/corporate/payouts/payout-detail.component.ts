import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription, interval } from 'rxjs';
import { ToastService } from '../../../core/services/toast.service';
import {
  ApprovalInstanceDetail,
  BatchProgress,
  PayoutBatch,
  PayoutItem,
  ReceiptArtifact,
} from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-payout-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressBarModule,
    MatPaginatorModule,
    TranslateModule,
  ],
  templateUrl: './payout-detail.component.html',
  styleUrl: './payout-detail.component.scss',
})
export class PayoutDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(CorporateApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);

  corporateId = '';
  batchId = '';
  batch: PayoutBatch | null = null;
  instanceDetail: ApprovalInstanceDetail | null = null;
  progress: BatchProgress | null = null;
  receipts: ReceiptArtifact[] = [];

  items: PayoutItem[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 50;

  private pollSub?: Subscription;

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    this.batchId = this.route.snapshot.paramMap.get('batchId') || '';

    if (this.corporateId && this.batchId) {
      this.loadData();
      this.startPollingIfProcessing();
    }
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  loadData() {
    this.api.getBatch(this.corporateId, this.batchId).subscribe({
      next: (b) => {
        this.batch = b;
        if (b.status === 'PENDING_APPROVAL' || b.status === 'APPROVED' || b.status === 'PROCESSING' || b.status === 'COMPLETED' || b.status === 'PARTIALLY_COMPLETED') {
          this.loadInstanceDetail();
        }
      },
    });

    this.loadItems();
    this.loadProgress();
    this.loadReceipts();
  }

  startPollingIfProcessing() {
    this.pollSub = interval(3000).subscribe(() => {
      if (this.batch && (this.batch.status === 'PROCESSING' || this.batch.status === 'RESERVING_FUNDS')) {
        this.loadData();
      }
    });
  }

  loadProgress() {
    this.api.getBatchProgress(this.corporateId, this.batchId).subscribe({
      next: (p) => (this.progress = p),
    });
  }

  loadInstanceDetail() {
    this.api.getInstanceDetail(this.batchId).subscribe({
      next: (d) => (this.instanceDetail = d),
    });
  }

  loadItems() {
    this.api.getBatchItems(this.corporateId, this.batchId, this.pageIndex, this.pageSize).subscribe({
      next: (res) => {
        this.items = res.content || [];
        this.totalElements = res.totalElements || 0;
      },
    });
  }

  loadReceipts() {
    this.api.getBatchReceipts(this.corporateId, this.batchId).subscribe({
      next: (list) => (this.receipts = list),
    });
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadItems();
  }

  cancelBatch() {
    this.api.cancelBatch(this.corporateId, this.batchId).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('CORPORATE.BATCH_CANCEL_SUCCESS'));
        this.loadData();
      },
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.BATCH_CANCEL_ERROR')),
    });
  }

  retryBatch() {
    this.api.retryBatch(this.corporateId, this.batchId).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('CORPORATE.BATCH_RETRY_STARTED'));
        this.loadData();
      },
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.BATCH_RETRY_ERROR')),
    });
  }

  downloadConsolidatedReport() {
    const report = this.receipts.find((r) => r.artifactType === 'CONSOLIDATED_BATCH_REPORT');
    if (!report) {
      this.toast.info(this.translate.instant('CORPORATE.REPORT_GENERATING'));
      return;
    }
    this.downloadFile(report.id, 'bao_cao_chi_tra_' + this.batchId + '.pdf');
  }

  downloadItemReceipt(artifactId: string) {
    this.downloadFile(artifactId, 'bien_lai_' + artifactId + '.pdf');
  }

  private downloadFile(artifactId: string, filename: string) {
    this.api.downloadReceipt(this.corporateId, artifactId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        window.URL.revokeObjectURL(url);
      },
    });
  }
}
