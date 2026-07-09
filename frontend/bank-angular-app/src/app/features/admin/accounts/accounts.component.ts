import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { Account } from '../../../core/models/domain.model';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-accounts',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, PageHeaderComponent, MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AdminAccountsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  account: Account | null = null;

  form = this.fb.nonNullable.group({
    accountId: ['', Validators.required],
  });

  lookup(): void {
    if (this.form.invalid) return;
    const id = this.form.controls.accountId.value.trim();
    this.api.getAccount(id).subscribe({
      next: (a) => (this.account = a),
      error: () => (this.account = null),
    });
  }

  freeze(): void {
    if (!this.account) return;
    this.api.freezeAccount(this.account.id).subscribe({
      next: (a) => {
        this.account = a;
        this.toast.success(this.i18n.instant('ADMIN.FROZEN_OK'));
      },
    });
  }

  unfreeze(): void {
    if (!this.account) return;
    this.api.unfreezeAccount(this.account.id).subscribe({
      next: (a) => {
        this.account = a;
        this.toast.success(this.i18n.instant('ADMIN.ACTIVE_OK'));
      },
    });
  }
}
