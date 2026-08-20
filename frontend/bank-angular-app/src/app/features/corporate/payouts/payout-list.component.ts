import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PayoutBatch } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-payout-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    TranslateModule,
  ],
  templateUrl: './payout-list.component.html',
  styleUrl: './payout-list.component.scss',
})
export class PayoutListComponent implements OnInit {
  private readonly api = inject(CorporateApiService);

  corporateId = '';
  batches: PayoutBatch[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 20;

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (this.corporateId) {
      this.loadBatches();
    }
  }

  loadBatches() {
    this.api.getBatches(this.corporateId, this.pageIndex, this.pageSize).subscribe({
      next: (res) => {
        this.batches = res.content || [];
        this.totalElements = res.totalElements || 0;
      },
    });
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadBatches();
  }

  downloadTemplate() {
    this.api.downloadTemplate(this.corporateId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'mau_chi_tra_luong.xlsx';
        a.click();
        window.URL.revokeObjectURL(url);
      },
    });
  }
}
