import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { CorporateAccount, CorporateMember, PayoutBatch } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-corporate-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTableModule,
    TranslateModule,
  ],
  templateUrl: './corporate-dashboard.component.html',
  styleUrl: './corporate-dashboard.component.scss',
})
export class CorporateDashboardComponent implements OnInit {
  private readonly api = inject(CorporateApiService);

  corporateId = '';
  accounts: CorporateAccount[] = [];
  members: CorporateMember[] = [];
  recentBatches: PayoutBatch[] = [];
  pendingBatchCount = 0;
  completedBatchCount = 0;

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (this.corporateId) {
      this.loadData();
    }
  }

  loadData() {
    this.api.getAccounts(this.corporateId).subscribe({
      next: (accs) => (this.accounts = accs),
    });
    this.api.getMembers(this.corporateId).subscribe({
      next: (mems) => (this.members = mems),
    });
    this.api.getBatches(this.corporateId, 0, 5).subscribe({
      next: (res) => {
        this.recentBatches = res.content || [];
        this.pendingBatchCount = this.recentBatches.filter(
          (b) => b.status === 'PENDING_APPROVAL' || b.status === 'PROCESSING'
        ).length;
        this.completedBatchCount = this.recentBatches.filter(
          (b) => b.status === 'COMPLETED'
        ).length;
      },
    });
  }
}
