import { Routes } from '@angular/router';

export const CORPORATE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./corporate-shell.component').then((m) => m.CorporateShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./dashboard/corporate-dashboard.component').then(
            (m) => m.CorporateDashboardComponent
          ),
      },
      {
        path: 'matrix',
        loadComponent: () =>
          import('./matrix/matrix-builder.component').then(
            (m) => m.MatrixBuilderComponent
          ),
      },
      {
        path: 'payouts',
        loadComponent: () =>
          import('./payouts/payout-list.component').then(
            (m) => m.PayoutListComponent
          ),
      },
      {
        path: 'payouts/create',
        loadComponent: () =>
          import('./payouts/payout-wizard.component').then(
            (m) => m.PayoutWizardComponent
          ),
      },
      {
        path: 'payouts/:batchId',
        loadComponent: () =>
          import('./payouts/payout-detail.component').then(
            (m) => m.PayoutDetailComponent
          ),
      },
      {
        path: 'approvals',
        loadComponent: () =>
          import('./approvals/approval-inbox.component').then(
            (m) => m.ApprovalInboxComponent
          ),
      },
      {
        path: 'accounts',
        loadComponent: () =>
          import('./accounts/corporate-accounts.component').then(
            (m) => m.CorporateAccountsComponent
          ),
      },
      {
        path: 'receipts',
        loadComponent: () =>
          import('./receipts/receipt-center.component').then(
            (m) => m.ReceiptCenterComponent
          ),
      },
    ],
  },
];
