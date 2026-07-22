import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService } from '../../../core/services/bank-api.service';
import { AuditLog } from '../../../core/models/domain.model';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatTableModule, MatPaginatorModule, PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AdminAuditComponent implements OnInit {
  private readonly api = inject(BankApiService);
  rows: AuditLog[] = [];
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  cols = ['createdAt', 'action', 'actorUserId', 'resourceType', 'resourceId', 'ip'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.auditLogs(this.pageIndex, this.pageSize).subscribe({
      next: (p) => {
        this.rows = p.items || [];
        this.totalElements = p.totalElements ?? this.rows.length;
      },
      error: () => {
        this.rows = [];
        this.totalElements = 0;
      },
    });
  }

  page(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }
}
