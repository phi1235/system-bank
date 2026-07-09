import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { BankApiService } from '../../../core/services/bank-api.service';
import { Transfer } from '../../../core/models/domain.model';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-transfers',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatTableModule, MatButtonModule, MatFormFieldModule, MatSelectModule, PageHeaderComponent, MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './transfers.component.html',
  styleUrl: './transfers.component.scss',
})
export class AdminTransfersComponent implements OnInit {
  private readonly api = inject(BankApiService);
  rows: Transfer[] = [];
  status = '';
  cols = ['createdAt', 'amount', 'status', 'fromAccountId', 'toAccountNumber', 'transactionId'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.adminTransfers(0, 50, this.status || undefined).subscribe({
      next: (p) => (this.rows = p.items || []),
    });
  }
}
