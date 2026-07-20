import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
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
    MatTooltipModule,
    MatDialogModule,
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
  private readonly dialog = inject(MatDialog);

  rows: Beneficiary[] = [];
  loading = false;
  saving = false;
  deletingId: string | null = null;
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
    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_TITLE'),
      message: this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_CONFIRM', {
        name: row.nickname,
        account: row.accountNumber,
      }),
      confirmLabel: this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE'),
      cancelLabel: this.i18n.instant('COMMON.CANCEL'),
      destructive: true,
    };

    this.dialog
      .open(ConfirmDialogComponent, {
        width: '420px',
        data,
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(filter((ok): ok is true => ok === true))
      .subscribe(() => {
        this.deletingId = row.id;
        this.api.deleteBeneficiary(row.id).subscribe({
          next: () => {
            this.deletingId = null;
            this.toast.success(this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_OK'));
            this.load();
          },
          error: (err) => {
            this.deletingId = null;
            this.toast.error(err?.message || this.i18n.instant('CUSTOMER.BENEFICIARY_DELETE_FAIL'));
          },
        });
      });
  }
}
