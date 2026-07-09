import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService } from '../../../core/services/bank-api.service';
import { AuditLog } from '../../../core/models/domain.model';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatTableModule, PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AdminAuditComponent implements OnInit {
  private readonly api = inject(BankApiService);
  rows: AuditLog[] = [];
  cols = ['createdAt', 'action', 'actorUserId', 'resourceType', 'resourceId', 'ip'];

  ngOnInit(): void {
    this.api.auditLogs(0, 50).subscribe({ next: (p) => (this.rows = p.items || []) });
  }
}
