import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { PayoutBatch } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-receipt-center',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatCardModule, TranslateModule],
  templateUrl: './receipt-center.component.html',
  styleUrl: './receipt-center.component.scss',
})
export class ReceiptCenterComponent implements OnInit {
  private readonly api = inject(CorporateApiService);

  corporateId = '';
  batches: PayoutBatch[] = [];

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (this.corporateId) {
      this.loadBatches();
    }
  }

  loadBatches() {
    this.api.getBatches(this.corporateId, 0, 50).subscribe({
      next: (res) => {
        this.batches = (res.content || []).filter(
          (b) => b.status === 'COMPLETED' || b.status === 'PARTIALLY_COMPLETED'
        );
      },
    });
  }

  downloadConsolidatedReport(batchId: string) {
    this.api.getBatchReceipts(this.corporateId, batchId).subscribe({
      next: (receipts) => {
        const report = receipts.find((r) => r.artifactType === 'CONSOLIDATED_BATCH_REPORT');
        if (report) {
          this.api.downloadReceipt(this.corporateId, report.id).subscribe({
            next: (blob) => {
              const url = window.URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = 'bao_cao_tong_hop_' + batchId + '.pdf';
              a.click();
              window.URL.revokeObjectURL(url);
            },
          });
        }
      },
    });
  }
}
