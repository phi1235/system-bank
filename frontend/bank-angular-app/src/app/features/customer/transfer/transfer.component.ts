import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialogModule } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import { selectAccounts } from '../../../store/accounts/accounts.selectors';
import { TransfersActions } from '../../../store/transfers/transfers.actions';
import { selectLastTransfer, selectTransferCreating, selectTransferError } from '../../../store/transfers/transfers.selectors';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-transfer',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    PageHeaderComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './transfer.component.html',
  styleUrl: './transfer.component.scss',
})
export class TransferComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly i18n = inject(TranslateService);
  accounts$ = this.store.select(selectAccounts);
  creating$ = this.store.select(selectTransferCreating);
  last$ = this.store.select(selectLastTransfer);
  error$ = this.store.select(selectTransferError);
  canExecute$ = this.store.select(selectHasPermission(PERMISSIONS.IB_TRANSFER_EXECUTE));

  form = this.fb.nonNullable.group({
    fromAccountId: ['', Validators.required],
    toAccountNumber: ['', [Validators.required, Validators.pattern(/^\d{8,14}$/)]],
    amount: [0, [Validators.required, Validators.min(1)]],
    description: [''],
  });

  ngOnInit(): void {
    this.form.patchValue({ description: this.i18n.instant('CUSTOMER.DEFAULT_DESC') });
    this.store.dispatch(AccountsActions.load());
    this.store.dispatch(TransfersActions.clearStatus());
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const ok = confirm(
      this.i18n.instant('CUSTOMER.CONFIRM_MSG', {
        amount: Number(v.amount).toLocaleString('vi-VN'),
        to: v.toAccountNumber,
        desc: v.description || '',
      }),
    );
    if (!ok) return;
    const key = crypto.randomUUID();
    this.store.dispatch(
      TransfersActions.create({
        request: {
          fromAccountId: v.fromAccountId,
          toAccountNumber: v.toAccountNumber,
          amount: Number(v.amount),
          description: v.description || undefined,
          currency: 'VND',
        },
        idempotencyKey: key,
      }),
    );
  }
}
