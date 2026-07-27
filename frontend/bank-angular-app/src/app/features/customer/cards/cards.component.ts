import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { Account, Card, CardReveal } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';

@Component({
  selector: 'app-cards',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    LoadingComponent,
    PageHeaderComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './cards.component.html',
  styleUrl: './cards.component.scss',
})
export class CardsComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  loading = false;
  cards: Card[] = [];
  accounts: Account[] = [];
  issueAccountId = '';
  busyId: string | null = null;
  issuing = false;

  /** Card id → revealed PAN (kept only in memory, auto-hidden). */
  revealed = new Map<string, CardReveal>();
  limitEditId: string | null = null;
  limitValue: number | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    forkJoin({ cards: this.api.myCards(), accounts: this.api.listAccounts() }).subscribe({
      next: ({ cards, accounts }) => {
        this.cards = cards;
        this.accounts = accounts.filter((a) => a.status === 'ACTIVE');
        if (!this.issueAccountId && this.accounts.length) {
          this.issueAccountId = this.accounts[0].id;
        }
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  issue(): void {
    if (!this.issueAccountId || this.issuing) {
      return;
    }
    this.issuing = true;
    this.api.issueCard(this.issueAccountId).subscribe({
      next: () => {
        this.issuing = false;
        this.toast.success(this.i18n.instant('CUSTOMER.CARDS_ISSUED'));
        this.load();
      },
      error: (err) => {
        this.issuing = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  action(card: Card, action: 'activate' | 'lock' | 'unlock'): void {
    if (this.busyId) {
      return;
    }
    this.busyId = card.id;
    this.api.cardAction(card.id, action).subscribe({
      next: () => {
        this.busyId = null;
        this.revealed.delete(card.id);
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  confirmClose(card: Card): void {
    const data: ConfirmDialogData = {
      title: this.i18n.instant('CUSTOMER.CARDS_CLOSE_TITLE'),
      message: this.i18n.instant('CUSTOMER.CARDS_CLOSE_WARN', {
        last4: card.maskedPan?.slice(-4) ?? '—',
      }),
      confirmLabel: this.i18n.instant('CUSTOMER.CARDS_CLOSE_CONFIRM'),
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .subscribe((ok) => {
        if (ok) {
          this.busyId = card.id;
          this.api.cardAction(card.id, 'close').subscribe({
            next: () => {
              this.busyId = null;
              this.revealed.delete(card.id);
              this.load();
            },
            error: (err) => {
              this.busyId = null;
              this.toast.error(resolveHttpErrorMessage(err, this.i18n));
            },
          });
        }
      });
  }

  toggleReveal(card: Card): void {
    if (this.revealed.has(card.id)) {
      this.revealed.delete(card.id);
      return;
    }
    this.api.revealCard(card.id).subscribe({
      next: (r) => {
        this.revealed.set(card.id, r);
        // Auto-hide after 30s — full PAN should not linger on screen
        setTimeout(() => this.revealed.delete(card.id), 30_000);
      },
      error: (err) => this.toast.error(resolveHttpErrorMessage(err, this.i18n)),
    });
  }

  startLimitEdit(card: Card): void {
    this.limitEditId = card.id;
    this.limitValue = card.dailyLimit;
  }

  saveLimit(card: Card): void {
    if (this.limitValue == null || this.limitValue < 0) {
      return;
    }
    this.api.updateCardLimit(card.id, this.limitValue).subscribe({
      next: () => {
        this.limitEditId = null;
        this.toast.success(this.i18n.instant('CUSTOMER.CARDS_LIMIT_SAVED'));
        this.load();
      },
      error: (err) => this.toast.error(resolveHttpErrorMessage(err, this.i18n)),
    });
  }

  formatPan(pan: string): string {
    return pan.replace(/(\d{4})(?=\d)/g, '$1 ');
  }

  get hasOpenCardAccounts(): Set<string> {
    return new Set(
      this.cards
        .filter((c) => c.status !== 'CLOSED' && c.status !== 'REJECTED')
        .map((c) => c.accountId),
    );
  }

  isIssued(c: Card): boolean {
    return c.status === 'PENDING_ACTIVATION' || c.status === 'ACTIVE' || c.status === 'LOCKED';
  }

  get issuableAccounts(): Account[] {
    const taken = this.hasOpenCardAccounts;
    return this.accounts.filter((a) => !taken.has(a.id));
  }
}
