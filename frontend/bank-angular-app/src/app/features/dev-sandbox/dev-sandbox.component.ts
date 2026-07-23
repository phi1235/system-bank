import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { BankApiService } from '../../core/services/bank-api.service';
import { ToastService } from '../../core/services/toast.service';
import { NotificationItem } from '../../core/models/domain.model';
import { copyText } from '../../core/utils/transfer-receipt.util';

@Component({
  selector: 'app-dev-sandbox',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './dev-sandbox.component.html',
  styleUrl: './dev-sandbox.component.scss',
})
export class DevSandboxComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  loading = false;
  items: NotificationItem[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;

  filterForm = this.fb.nonNullable.group({
    q: [''],
    channel: ['ALL'],
  });

  displayedColumns = ['channel', 'recipient', 'template', 'body', 'createdAt', 'actions'];

  ngOnInit(): void {
    this.loadData();
    this.filterForm.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => {
        this.pageIndex = 0;
        this.loadData();
      });
  }

  loadData(): void {
    this.loading = true;
    const v = this.filterForm.getRawValue();
    this.api
      .getNotificationSandbox({
        q: v.q?.trim() || undefined,
        channel: v.channel !== 'ALL' ? v.channel : undefined,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .subscribe({
        next: (res) => {
          this.loading = false;
          this.items = res.items || [];
          this.totalElements = res.totalElements || 0;
        },
        error: () => {
          this.loading = false;
          this.toast.error(this.i18n.instant('DEV_SANDBOX.LOAD_FAIL'));
        },
      });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  async copyBody(item: NotificationItem): Promise<void> {
    const otpMatch = item.body?.match(/\b\d{6}\b/);
    const textToCopy = otpMatch ? otpMatch[0] : item.body;
    const ok = await copyText(textToCopy);
    this.toast[ok ? 'success' : 'error'](
      otpMatch
        ? this.i18n.instant('DEV_SANDBOX.COPY_OTP_OK', { otp: otpMatch[0] })
        : this.i18n.instant('DEV_SANDBOX.COPY_BODY_OK')
    );
  }

  extractOtp(body: string): string | null {
    const match = body?.match(/\b\d{6}\b/);
    return match ? match[0] : null;
  }

  refresh(): void {
    this.loadData();
  }
}
