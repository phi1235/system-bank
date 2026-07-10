import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService } from '../../../core/services/bank-api.service';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    RouterLink,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit {
  private readonly api = inject(BankApiService);
  customers = 0;
  transfers = 0;
  audits = 0;

  ngOnInit(): void {
    this.api.listCustomers(0, 1).subscribe({
      next: (p) => (this.customers = p.totalElements ?? (p as any).items?.length ?? 0),
      error: () => {},
    });
    this.api.adminTransfers(0, 1).subscribe({
      next: (p) => (this.transfers = p.totalElements ?? 0),
      error: () => {},
    });
    this.api.auditLogs(0, 1).subscribe({
      next: (p) => (this.audits = p.totalElements ?? 0),
      error: () => {},
    });
  }
}
