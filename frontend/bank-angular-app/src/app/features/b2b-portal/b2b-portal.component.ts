import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, finalize, takeUntil } from 'rxjs';
import {
  B2bClientApp,
  B2bConsent,
  B2bMetric,
  B2bSandboxResponse,
  BankApiService,
} from '../../core/services/bank-api.service';
import { ToastService } from '../../core/services/toast.service';

export interface B2bScopeMeta {
  key: string;
  nameKey: string;
  descKey: string;
  category: 'AIS' | 'PIS';
  icon: string;
}

export const OPEN_BANKING_SCOPES: B2bScopeMeta[] = [
  {
    key: 'openbanking:accounts:read',
    nameKey: 'B2B_PORTAL.SCOPES.ACCOUNTS_READ',
    descKey: 'B2B_PORTAL.SCOPES.ACCOUNTS_READ_DESC',
    category: 'AIS',
    icon: 'account_balance_wallet',
  },
  {
    key: 'openbanking:statements:read',
    nameKey: 'B2B_PORTAL.SCOPES.STATEMENTS_READ',
    descKey: 'B2B_PORTAL.SCOPES.STATEMENTS_READ_DESC',
    category: 'AIS',
    icon: 'receipt_long',
  },
  {
    key: 'openbanking:payments:write',
    nameKey: 'B2B_PORTAL.SCOPES.PAYMENTS_WRITE',
    descKey: 'B2B_PORTAL.SCOPES.PAYMENTS_WRITE_DESC',
    category: 'PIS',
    icon: 'send',
  },
  {
    key: 'openbanking:payments:bulk:write',
    nameKey: 'B2B_PORTAL.SCOPES.PAYMENTS_BULK_WRITE',
    descKey: 'B2B_PORTAL.SCOPES.PAYMENTS_BULK_WRITE_DESC',
    category: 'PIS',
    icon: 'payments',
  },
  {
    key: 'openbanking:payments:read',
    nameKey: 'B2B_PORTAL.SCOPES.PAYMENTS_READ',
    descKey: 'B2B_PORTAL.SCOPES.PAYMENTS_READ_DESC',
    category: 'PIS',
    icon: 'manage_search',
  },
];

export interface B2bConsentPermMeta {
  key: string;
  nameKey: string;
  descKey: string;
  icon: string;
}

export const CONSENT_PERMISSIONS: B2bConsentPermMeta[] = [
  {
    key: 'ReadAccountsDetail',
    nameKey: 'B2B_PORTAL.CONSENT_PERMS.READ_ACCOUNTS',
    descKey: 'B2B_PORTAL.CONSENT_PERMS.READ_ACCOUNTS_DESC',
    icon: 'account_circle',
  },
  {
    key: 'ReadBalances',
    nameKey: 'B2B_PORTAL.CONSENT_PERMS.READ_BALANCES',
    descKey: 'B2B_PORTAL.CONSENT_PERMS.READ_BALANCES_DESC',
    icon: 'account_balance_wallet',
  },
  {
    key: 'ReadStatements',
    nameKey: 'B2B_PORTAL.CONSENT_PERMS.READ_STATEMENTS',
    descKey: 'B2B_PORTAL.CONSENT_PERMS.READ_STATEMENTS_DESC',
    icon: 'receipt_long',
  },
  {
    key: 'CreateSinglePayment',
    nameKey: 'B2B_PORTAL.CONSENT_PERMS.CREATE_SINGLE_PAYMENT',
    descKey: 'B2B_PORTAL.CONSENT_PERMS.CREATE_SINGLE_PAYMENT_DESC',
    icon: 'send',
  },
  {
    key: 'CreateBulkPayment',
    nameKey: 'B2B_PORTAL.CONSENT_PERMS.CREATE_BULK_PAYMENT',
    descKey: 'B2B_PORTAL.CONSENT_PERMS.CREATE_BULK_PAYMENT_DESC',
    icon: 'payments',
  },
];

@Component({
  selector: 'app-b2b-portal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTabsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatChipsModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDialogModule,
    MatDividerModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './b2b-portal.component.html',
  styleUrl: './b2b-portal.component.scss',
})
export class B2bPortalComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  selectedTab = 0;
  readonly availableScopes = OPEN_BANKING_SCOPES;
  readonly availableConsentPerms = CONSENT_PERMISSIONS;

  // ── Tab 1: Apps & Keys ──
  apps: B2bClientApp[] = [];
  loadingApps = false;
  searchAppQuery = '';
  showAppModal = false;
  editingApp = false;
  selectedAppForDetail: B2bClientApp | null = null;
  showDetailModal = false;
  selectedScopesMap: { [key: string]: boolean } = {};
  appForm: Partial<B2bClientApp> = {
    clientId: '',
    clientName: '',
    organizationTaxCode: '',
    tokenEndpointAuthMethod: 'private_key_jwt',
    allowedScopes: 'openbanking:accounts:read openbanking:statements:read openbanking:payments:write openbanking:payments:bulk:write openbanking:payments:read',
    publicKeyPem: '',
    clientCertThumbprintSha256: '',
    webhookCallbackUrl: '',
    webhookSecret: '',
    rateLimitRpm: 120,
    status: 'ACTIVE',
  };
  savingApp = false;

  // ── Tab 2: Account Consents ──
  consents: B2bConsent[] = [];
  loadingConsents = false;
  showConsentModal = false;
  selectedConsentPermsMap: { [key: string]: boolean } = {};
  consentForm = {
    clientId: '',
    accountNumber: '',
    permissions: 'ReadAccountsDetail,ReadBalances,ReadStatements,CreateSinglePayment,CreateBulkPayment',
  };
  savingConsent = false;

  // ── Scope Helpers ──
  getScopeList(scopesStr?: string): string[] {
    if (!scopesStr) return [];
    return scopesStr.trim().split(/\s+/).filter(Boolean);
  }

  getScopeMeta(scopeKey: string): B2bScopeMeta | undefined {
    return this.availableScopes.find((s) => s.key === scopeKey);
  }

  getScopeLabel(scopeKey: string): string {
    const meta = this.getScopeMeta(scopeKey);
    return meta ? this.i18n.instant(meta.nameKey) : scopeKey;
  }

  getScopeIcon(scopeKey: string): string {
    const meta = this.getScopeMeta(scopeKey);
    return meta ? meta.icon : 'security';
  }

  getScopeCategory(scopeKey: string): string {
    const meta = this.getScopeMeta(scopeKey);
    return meta ? meta.category : 'AIS';
  }

  getAuthMethodLabel(method?: string): string {
    if (!method) return '—';
    const key = `B2B_PORTAL.AUTH_METHODS.${method}`;
    const translated = this.i18n.instant(key);
    return translated !== key ? translated : method;
  }

  getConsentPermLabel(permKey: string): string {
    const meta = this.availableConsentPerms.find((p) => p.key === permKey);
    return meta ? this.i18n.instant(meta.nameKey) : permKey;
  }

  getConsentPermList(permsStr?: string): string[] {
    if (!permsStr) return [];
    return permsStr.split(',').map((p) => p.trim()).filter(Boolean);
  }

  // ── Tab 3: Interactive ISO 20022 Sandbox ──
  sandboxClientId = 'client_misa_erp_prod';
  sandboxMessageType = 'pain.001';
  sandboxFormat = 'JSON';
  sandboxPayload = '';
  executingSandbox = false;
  sandboxResponse: B2bSandboxResponse | null = null;

  // ── Tab 4: Live Traffic Metrics & Logs ──
  metrics: B2bMetric[] = [];
  loadingMetrics = false;

  ngOnInit(): void {
    this.loadApps();
    this.loadConsents();
    this.loadMetrics();
    this.loadSandboxTemplate();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Apps Management Methods ──

  loadApps(): void {
    this.loadingApps = true;
    this.api
      .getB2bClients({ q: this.searchAppQuery || undefined })
      .pipe(
        finalize(() => (this.loadingApps = false)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (res) => {
          this.apps = res || [];
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.LOAD_APPS_ERROR'));
        },
      });
  }

  openCreateAppModal(): void {
    this.editingApp = false;
    this.selectedScopesMap = {
      'openbanking:accounts:read': true,
      'openbanking:statements:read': true,
      'openbanking:payments:write': true,
      'openbanking:payments:bulk:write': true,
      'openbanking:payments:read': true,
    };
    this.appForm = {
      clientId: 'client_enterprise_' + Math.random().toString(36).substring(2, 7),
      clientName: '',
      organizationTaxCode: '',
      tokenEndpointAuthMethod: 'private_key_jwt',
      allowedScopes: '',
      publicKeyPem: '',
      clientCertThumbprintSha256: '',
      webhookCallbackUrl: '',
      webhookSecret: '',
      rateLimitRpm: 120,
      status: 'ACTIVE',
    };
    this.showAppModal = true;
  }

  openDetailModal(app: B2bClientApp): void {
    this.selectedAppForDetail = app;
    this.showDetailModal = true;
  }

  closeDetailModal(): void {
    this.showDetailModal = false;
    this.selectedAppForDetail = null;
  }

  copyText(text?: string): void {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      this.toast.success(this.i18n.instant('B2B_PORTAL.APPS.COPIED'));
    });
  }

  openEditAppModal(app: B2bClientApp): void {
    this.editingApp = true;
    this.selectedScopesMap = {};
    const scopes = this.getScopeList(app.allowedScopes);
    this.availableScopes.forEach((s) => {
      this.selectedScopesMap[s.key] = scopes.includes(s.key);
    });
    this.appForm = { ...app };
    this.showAppModal = true;
  }

  closeAppModal(): void {
    this.showAppModal = false;
  }

  saveApp(): void {
    if (!this.appForm.clientId || !this.appForm.clientName || !this.appForm.organizationTaxCode) {
      this.toast.error(this.i18n.instant('B2B_PORTAL.MESSAGES.REQUIRED_FIELDS'));
      return;
    }

    // Pack selected scopes from map into space-separated string
    const chosenScopes = Object.keys(this.selectedScopesMap).filter((k) => this.selectedScopesMap[k]);
    this.appForm.allowedScopes = chosenScopes.join(' ');

    this.savingApp = true;
    if (this.editingApp) {
      this.api
        .updateB2bClient(this.appForm.clientId, this.appForm)
        .pipe(
          finalize(() => (this.savingApp = false)),
          takeUntil(this.destroy$)
        )
        .subscribe({
          next: () => {
            this.toast.success(this.i18n.instant('B2B_PORTAL.MESSAGES.UPDATE_APP_SUCCESS'));
            this.closeAppModal();
            this.loadApps();
          },
          error: (err) => {
            this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.SAVE_APP_ERROR'));
          },
        });
    } else {
      this.api
        .createB2bClient(this.appForm)
        .pipe(
          finalize(() => (this.savingApp = false)),
          takeUntil(this.destroy$)
        )
        .subscribe({
          next: () => {
            this.toast.success(this.i18n.instant('B2B_PORTAL.MESSAGES.CREATE_APP_SUCCESS'));
            this.closeAppModal();
            this.loadApps();
          },
          error: (err) => {
            this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.SAVE_APP_ERROR'));
          },
        });
    }
  }

  deleteApp(app: B2bClientApp): void {
    if (!confirm(this.i18n.instant('B2B_PORTAL.MESSAGES.CONFIRM_DELETE_APP', { name: app.clientName }))) {
      return;
    }
    this.api
      .deleteB2bClient(app.clientId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.instant('B2B_PORTAL.MESSAGES.DELETE_APP_SUCCESS'));
          this.loadApps();
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.DELETE_APP_ERROR'));
        },
      });
  }

  // ── Consents Management Methods ──

  loadConsents(): void {
    this.loadingConsents = true;
    this.api
      .getB2bConsents()
      .pipe(
        finalize(() => (this.loadingConsents = false)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (res) => {
          this.consents = res || [];
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.LOAD_CONSENTS_ERROR'));
        },
      });
  }

  openGrantConsentModal(): void {
    this.consentForm = {
      clientId: this.apps[0]?.clientId || 'client_misa_erp_prod',
      accountNumber: '10987654321',
      permissions: 'ReadAccountsDetail,ReadBalances,ReadStatements,CreateSinglePayment,CreateBulkPayment',
    };
    this.showConsentModal = true;
  }

  closeConsentModal(): void {
    this.showConsentModal = false;
  }

  saveConsent(): void {
    if (!this.consentForm.clientId || !this.consentForm.accountNumber) {
      this.toast.error(this.i18n.instant('B2B_PORTAL.MESSAGES.REQUIRED_FIELDS'));
      return;
    }
    this.savingConsent = true;
    this.api
      .createB2bConsent(this.consentForm)
      .pipe(
        finalize(() => (this.savingConsent = false)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.instant('B2B_PORTAL.MESSAGES.GRANT_CONSENT_SUCCESS'));
          this.closeConsentModal();
          this.loadConsents();
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.SAVE_CONSENT_ERROR'));
        },
      });
  }

  revokeConsent(consent: B2bConsent): void {
    this.api
      .revokeB2bConsent(consent.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.instant('B2B_PORTAL.MESSAGES.REVOKE_CONSENT_SUCCESS'));
          this.loadConsents();
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.REVOKE_CONSENT_ERROR'));
        },
      });
  }

  // ── ISO 20022 Sandbox Methods ──

  loadSandboxTemplate(): void {
    if (this.sandboxMessageType === 'pain.001') {
      this.sandboxPayload = JSON.stringify(
        {
          groupHeader: {
            messageIdentification: 'MSG-ERP-TEST-' + Math.floor(1000 + Math.random() * 9000),
            creationDateTime: new Date().toISOString(),
            numberOfTransactions: 1,
            controlSum: 15000000.0,
            initiatingParty: {
              name: 'CONG TY CO PHAN CONG NGHE ABC',
              identification: 'TAX-0101234567',
            },
          },
          paymentInformation: [
            {
              paymentInformationIdentification: 'PAY-INFO-001',
              paymentMethod: 'TRF',
              requestedExecutionDate: new Date().toISOString().split('T')[0],
              debtor: { name: 'CONG TY CO PHAN CONG NGHE ABC' },
              debtorAccount: { accountNumber: '10987654321', currency: 'VND' },
              debtorAgent: { bic: 'SYSBVNVX' },
              creditTransferTransactionInformation: [
                {
                  paymentIdentification: {
                    instructionIdentification: 'INS-' + Math.floor(1000 + Math.random() * 9000),
                    endToEndIdentification: 'E2E-INV-' + Math.floor(1000 + Math.random() * 9000),
                  },
                  amount: { currency: 'VND', value: 15000000.0 },
                  creditor: { name: 'NGUYEN VAN B' },
                  creditorAccount: { accountNumber: '98765432109', currency: 'VND' },
                  creditorAgent: { bic: 'VCBVNVX', bankCode: '970436' },
                  remittanceInformation: { unstructured: 'Thanh toan tien hoa don B2B' },
                },
              ],
            },
          ],
        },
        null,
        2
      );
    } else {
      this.sandboxPayload = JSON.stringify(
        {
          groupHeader: {
            messageIdentification: 'STMT-REQ-' + Math.floor(1000 + Math.random() * 9000),
            creationDateTime: new Date().toISOString(),
          },
          accountNumber: '10987654321',
          statementDate: new Date().toISOString().split('T')[0],
        },
        null,
        2
      );
    }
  }

  runSandbox(): void {
    if (!this.sandboxPayload) {
      this.toast.error(this.i18n.instant('B2B_PORTAL.MESSAGES.REQUIRED_PAYLOAD'));
      return;
    }
    this.executingSandbox = true;
    this.sandboxResponse = null;
    this.api
      .executeB2bSandbox({
        clientId: this.sandboxClientId,
        messageType: this.sandboxMessageType,
        format: this.sandboxFormat,
        payload: this.sandboxPayload,
      })
      .pipe(
        finalize(() => (this.executingSandbox = false)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (res) => {
          this.sandboxResponse = res;
          this.toast.success(this.i18n.instant('B2B_PORTAL.MESSAGES.SANDBOX_EXECUTE_SUCCESS'));
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.SANDBOX_EXECUTE_ERROR'));
        },
      });
  }

  // ── Metrics Methods ──

  loadMetrics(): void {
    this.loadingMetrics = true;
    this.api
      .getB2bMetrics()
      .pipe(
        finalize(() => (this.loadingMetrics = false)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (res) => {
          this.metrics = res || [];
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.LOAD_METRICS_ERROR'));
        },
      });
  }
}
