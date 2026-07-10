import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { Account } from '../../../core/models/domain.model';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-admin-accounts',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    PageHeaderComponent,
    MoneyVndPipe,
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
  private readonly store = inject(Store);
  account: Account | null = null;
  canFreeze$ = this.store.select(selectHasPermission(PERMISSIONS.ACCOUNTS_FREEZE_EXECUTE));

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
