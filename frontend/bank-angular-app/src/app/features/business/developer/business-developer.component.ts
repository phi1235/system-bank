import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import {
  MerchantAccountConfig,
  MerchantCredential,
  MerchantWebhookEndpoint,
} from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-business-developer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatDividerModule,
    TranslateModule,
  ],
  templateUrl: './business-developer.component.html',
  styleUrl: './business-developer.component.scss',
})
export class BusinessDeveloperComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  // Merchant Account Config
  collectionAccountId = '';
  escrowAccountId = '';
  savingConfig = false;

  // Credentials
  credentials: MerchantCredential[] = [];
  credColumns: string[] = ['name', 'keyId', 'createdAt', 'actions'];
  showCreateKeyModal = false;
  newKeyName = '';
  creatingKey = false;
  newlyCreatedSecret = '';
  showSecretModal = false;

  // Webhooks
  webhooks: MerchantWebhookEndpoint[] = [];
  webhookColumns: string[] = ['url', 'status', 'createdAt', 'actions'];
  showWebhookModal = false;
  newWebhookUrl = '';
  registeringWebhook = false;

  ngOnInit(): void {
    this.businessContext.selectedOrg$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadAll();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadAll(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.getMerchantAccountConfig(orgId).subscribe({
      next: (cfg) => {
        if (cfg) {
          this.collectionAccountId = cfg.collectionAccountId || '';
          this.escrowAccountId = cfg.escrowAccountId || '';
        } else {
          this.collectionAccountId = '';
          this.escrowAccountId = '';
        }
      },
      error: () => {},
    });

    this.api.listMerchantCredentials(orgId).subscribe({
      next: (keys) => (this.credentials = keys || []),
      error: () => {},
    });

    this.api.listMerchantWebhooks(orgId).subscribe({
      next: (whs) => (this.webhooks = whs || []),
      error: () => {},
    });
  }

  saveAccountConfig(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!this.collectionAccountId.trim() || !this.escrowAccountId.trim()) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }

    this.savingConfig = true;
    this.api
      .configureMerchantAccount(orgId, {
        collectionAccountId: this.collectionAccountId.trim(),
        escrowAccountId: this.escrowAccountId.trim(),
        defaultCurrency: 'VND',
      })
      .subscribe({
        next: () => {
          this.savingConfig = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        },
        error: (err) => {
          this.savingConfig = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  openCreateKeyModal(): void {
    this.newKeyName = '';
    this.showCreateKeyModal = true;
  }

  closeCreateKeyModal(): void {
    this.showCreateKeyModal = false;
  }

  submitCreateKey(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!this.newKeyName.trim()) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }

    this.creatingKey = true;
    this.api
      .createMerchantCredential(orgId, { name: this.newKeyName.trim() })
      .subscribe({
        next: (cred) => {
          this.creatingKey = false;
          this.showCreateKeyModal = false;
          this.newlyCreatedSecret = cred.secretKey || '';
          this.showSecretModal = true;
          this.loadAll();
        },
        error: (err) => {
          this.creatingKey = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  closeSecretModal(): void {
    this.showSecretModal = false;
    this.newlyCreatedSecret = '';
  }

  deleteKey(cred: MerchantCredential): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.deleteMerchantCredential(orgId, cred.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadAll();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }

  openWebhookModal(): void {
    this.newWebhookUrl = '';
    this.showWebhookModal = true;
  }

  closeWebhookModal(): void {
    this.showWebhookModal = false;
  }

  submitWebhook(): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    if (!this.newWebhookUrl.trim()) {
      this.toast.error(this.i18n.instant('VALIDATION.REQUIRED'));
      return;
    }

    this.registeringWebhook = true;
    this.api
      .registerMerchantWebhook(orgId, { url: this.newWebhookUrl.trim() })
      .subscribe({
        next: () => {
          this.registeringWebhook = false;
          this.showWebhookModal = false;
          this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
          this.loadAll();
        },
        error: (err) => {
          this.registeringWebhook = false;
          this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
        },
      });
  }

  deleteWebhook(wh: MerchantWebhookEndpoint): void {
    const orgId = this.businessContext.getSelectedOrgId();
    if (!orgId) return;

    this.api.deleteMerchantWebhook(orgId, wh.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('TOAST.SUCCESS'));
        this.loadAll();
      },
      error: (err) => {
        this.toast.error(err?.error?.error?.message || this.i18n.instant('TOAST.ERROR'));
      },
    });
  }
}
