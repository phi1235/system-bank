import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../../core/services/toast.service';
import { CorporateAccount } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-corporate-accounts',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDialogModule,
    TranslateModule,
  ],
  templateUrl: './corporate-accounts.component.html',
  styleUrl: './corporate-accounts.component.scss',
})
export class CorporateAccountsComponent implements OnInit {
  private readonly api = inject(CorporateApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  corporateId = '';
  accounts: CorporateAccount[] = [];

  ngOnInit() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (this.corporateId) {
      this.loadAccounts();
    }
  }

  loadAccounts() {
    this.corporateId = localStorage.getItem('selected_corp_id') || '';
    if (!this.corporateId) {
      this.accounts = [];
      return;
    }
    this.api.getAccounts(this.corporateId).subscribe({
      next: (list) => (this.accounts = list),
      error: () => (this.accounts = []),
    });
  }

  createAccount() {
    this.corporateId = localStorage.getItem('selected_corp_id') || this.corporateId;
    if (!this.corporateId) {
      this.toast.error(
        this.translate.instant('CORPORATE.SELECT_CORP_FIRST') ||
          'Vui lòng đăng ký hoặc chọn doanh nghiệp trước khi mở tài khoản.'
      );
      return;
    }
    this.api.createAndLinkAccount(this.corporateId, 'PAYMENT', 'VND').subscribe({
      next: (acc) => {
        this.toast.success(this.translate.instant('CORPORATE.ACCOUNT_CREATED', { accountNumber: acc.accountNumber }));
        this.loadAccounts();
      },
      error: (err) =>
        this.toast.error(
          err?.error?.error?.message ||
            err?.error?.message ||
            err.message ||
            this.translate.instant('CORPORATE.ACCOUNT_CREATE_ERROR')
        ),
    });
  }
}
