import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
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

  // ── Tab 1: Apps & Keys ──
  apps: B2bClientApp[] = [];
  loadingApps = false;
  searchAppQuery = '';
  showAppModal = false;
  editingApp = false;
  appForm: Partial<B2bClientApp> = {
    clientId: '',
    clientName: '',
    organizationTaxCode: '',
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
  consentForm = {
    clientId: '',
    accountNumber: '',
    permissions: 'ReadAccountsDetail,ReadBalances,ReadStatements,CreateSinglePayment,CreateBulkPayment',
  };
  savingConsent = false;

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
          this.apps = res?.items || [];
        },
        error: (err) => {
          this.toast.error(err?.message || this.i18n.instant('B2B_PORTAL.MESSAGES.LOAD_APPS_ERROR'));
        },
      });
  }

  openCreateAppModal(): void {
    this.editingApp = false;
    this.appForm = {
      clientId: 'client_enterprise_' + Math.random().toString(36).substring(2, 7),
      clientName: '',
      organizationTaxCode: '',
      allowedScopes: 'openbanking:accounts:read openbanking:statements:read openbanking:payments:write openbanking:payments:bulk:write openbanking:payments:read',
      publicKeyPem: '',
      clientCertThumbprintSha256: '',
      webhookCallbackUrl: '',
      webhookSecret: '',
      rateLimitRpm: 120,
      status: 'ACTIVE',
    };
    this.showAppModal = true;
  }

  openEditAppModal(app: B2bClientApp): void {
    this.editingApp = true;
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
          this.consents = res?.items || [];
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
