import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { Beneficiary } from '../../../core/models/domain.model';

@Component({
  selector: 'app-beneficiaries',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './beneficiaries.component.html',
  styleUrl: './beneficiaries.component.scss',
})
export class BeneficiariesComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  rows: Beneficiary[] = [];
  loading = false;
  saving = false;
  cols = ['nickname', 'accountNumber', 'actions'];

  form = this.fb.nonNullable.group({
    nickname: ['', [Validators.required, Validators.maxLength(80)]],
    accountNumber: ['', [Validators.required, Validators.pattern(/^\d{8,14}$/)]],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.listBeneficiaries().subscribe({
      next: (items) => {
        this.rows = items || [];
        this.loading = false;
      },
      error: () => {
        this.rows = [];
        this.loading = false;
        this.toast.error(this.i18n.instant('CUSTOMER.BENEFICIARY_LOAD_FAIL'));
      },
    });
  }

  create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    const v = this.form.getRawValue();
    this.api.createBeneficiary(v).subscribe({
      next: () => {
        this.saving = false;
        this.form.reset({ nickname: '', accountNumber: '' });
        this.toast.success(this.i18n.instant('CUSTOMER.BENEFICIARY_CREATE_OK'));
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.toast.error(err?.message || this.i18n.instant('CUSTOMER.BENEFICIARY_CREATE_FAIL'));
      },
    });
  }

  remove(row: Beneficiary): void {
    const ok = confirm(
      this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_CONFIRM', {
        name: row.nickname,
        account: row.accountNumber,
      }),
    );
    if (!ok) return;
    this.api.deleteBeneficiary(row.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_OK'));
        this.load();
      },
      error: (err) => {
        this.toast.error(err?.message || this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_FAIL'));
      },
    });
  }
}
