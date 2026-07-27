import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminCard } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { RejectCardDialogComponent } from './reject-card-dialog.component';

export interface CardDetailDialogData {
  card: AdminCard;
  canDecide: boolean;
}

@Component({
  selector: 'app-card-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatTooltipModule,
    MoneyVndPipe,
    TranslateModule,
  ],
  template: `
    <div class="card-detail-modal">
      <div mat-dialog-title class="dialog-header">
        <div class="header-left">
          <mat-icon class="card-icon">credit_score</mat-icon>
          <div>
            <h2 class="title">{{ 'ADMIN.CARDS_DETAIL_TITLE' | translate }}</h2>
            <span class="sub-id">ID: <code>{{ data.card.id }}</code></span>
          </div>
        </div>
        <span class="chip"
              [class.warn]="data.card.status === 'REQUESTED' || data.card.status === 'PENDING_ACTIVATION'"
              [class.ok]="data.card.status === 'ACTIVE'"
              [class.bad]="data.card.status === 'REJECTED' || data.card.status === 'CLOSED'">
          {{ 'CUSTOMER.CARDS_STATUS_' + data.card.status | translate }}
        </span>
      </div>

      <mat-dialog-content class="dialog-body">
        <div class="info-grid">
          <!-- Customer Info -->
          <div class="info-card customer-box">
            <div class="box-title">
              <mat-icon>person</mat-icon> {{ 'ADMIN.CARDS_CUSTOMER_INFO' | translate }}
            </div>
            <div class="row">
              <span class="lbl">{{ 'ADMIN.DEPOSITS_OWNER' | translate }}:</span>
              <strong class="val">{{ data.card.ownerName || '—' }}</strong>
            </div>
            <div class="row">
              <span class="lbl">User ID:</span>
              <code class="val-code">{{ data.card.userId }}</code>
            </div>
            <div class="row">
              <span class="lbl">{{ 'ADMIN.CARDS_KYC_STATUS' | translate }}:</span>
              <span class="kyc-badge">
                <mat-icon>verified</mat-icon> {{ 'ADMIN.CARDS_KYC_VERIFIED' | translate }}
              </span>
            </div>
          </div>

          <!-- Account & Request Info -->
          <div class="info-card account-box">
            <div class="box-title">
              <mat-icon>account_balance</mat-icon> {{ 'ADMIN.CARDS_ACCOUNT_INFO' | translate }}
            </div>
            <div class="row">
              <span class="lbl">{{ 'CUSTOMER.WEALTH_SOURCE' | translate }}:</span>
              <strong class="val stk">{{ data.card.accountNumber || '—' }}</strong>
            </div>
            <div class="row">
              <span class="lbl">{{ 'CUSTOMER.CARDS_DAILY_LIMIT' | translate }}:</span>
              <strong class="val money">{{ data.card.dailyLimit | moneyVnd }}</strong>
            </div>
            <div class="row">
              <span class="lbl">{{ 'ADMIN.CARDS_REQUESTED_AT' | translate }}:</span>
              <span class="val">{{ data.card.requestedAt | date:'medium' }}</span>
            </div>
          </div>
        </div>

        <!-- Identity Document (CCCD) Mockup Preview Section -->
        <div class="cccd-section">
          <div class="section-title">
            <mat-icon>badge</mat-icon> {{ 'ADMIN.CARDS_IDENTITY_DOC' | translate }}
          </div>
          <div class="cccd-cards">
            <!-- Front Card -->
            <div class="cccd-mock front">
              <div class="cccd-header">
                <div class="flag">🇻🇳</div>
                <div class="header-text">
                  <div class="c-title">{{ 'ADMIN.CARDS_NATIONAL_TITLE' | translate }}</div>
                  <div class="c-sub">{{ 'ADMIN.CARDS_IDENTITY_TITLE' | translate }}</div>
                </div>
              </div>
              <div class="cccd-body">
                <div class="avatar-placeholder">
                  <mat-icon>account_box</mat-icon>
                </div>
                <div class="cccd-details">
                  <div class="c-num">No: <strong>038092******</strong></div>
                  <div class="c-name">{{ data.card.ownerName || 'KHACH HANG' }}</div>
                  <div class="c-meta">{{ 'ADMIN.CARDS_NATIONALITY' | translate }}</div>
                </div>
              </div>
              <div class="watermark-ok">
                <mat-icon>check_circle</mat-icon> {{ 'ADMIN.CARDS_FACIAL_MATCH' | translate }}
              </div>
            </div>

            <!-- Back Card -->
            <div class="cccd-mock back">
              <div class="chip-graphic">
                <div class="sim-chip"></div>
                <span class="chip-text">{{ 'ADMIN.CARDS_CHIP_BIOMETRIC' | translate }}</span>
              </div>
              <div class="fingerprint-box">
                <mat-icon>fingerprint</mat-icon>
                <span>{{ 'ADMIN.CARDS_BIOMETRIC_VERIFIED' | translate }}</span>
              </div>
              <div class="mrz-line">
                IDVNM0380920001<<<<<<<<<<<<<<<
                9205104M2810105VNM<<<<<<<<<<<8
              </div>
            </div>
          </div>
        </div>

        @if (data.card.rejectReason) {
          <div class="reject-reason-box">
            <mat-icon color="warn">info</mat-icon>
            <div>
              <strong>{{ 'CUSTOMER.CARDS_REJECT_REASON' | translate }}:</strong> {{ data.card.rejectReason }}
            </div>
          </div>
        }
      </mat-dialog-content>

      <div mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button type="button" (click)="close()">
          {{ 'COMMON.CANCEL' | translate }}
        </button>

        @if (data.card.status === 'REQUESTED' && data.canDecide) {
          <button mat-stroked-button color="warn" type="button" (click)="reject()" [disabled]="busy">
            <mat-icon>close</mat-icon> {{ 'ADMIN.CARDS_REJECT_BTN' | translate }}
          </button>
          <button mat-flat-button color="primary" type="button" (click)="approve()" [disabled]="busy">
            <mat-icon>check</mat-icon> {{ 'ADMIN.CARDS_APPROVE_BTN' | translate }}
          </button>
        }
      </div>
    </div>
  `,
  styles: [`
    .card-detail-modal {
      padding: 8px;
    }
    .dialog-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 12px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      margin-bottom: 16px;

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .card-icon {
          font-size: 32px;
          width: 32px;
          height: 32px;
          color: #fbbf24;
        }

        .title {
          margin: 0;
          font-size: 1.15rem;
          font-weight: 700;
        }

        .sub-id {
          font-size: 0.8rem;
          opacity: 0.7;

          code {
            font-family: monospace;
            background: rgba(255, 255, 255, 0.08);
            padding: 2px 6px;
            border-radius: 4px;
          }
        }
      }
    }

    .chip {
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 600;
      background: rgba(255, 255, 255, 0.1);
      color: #94a3b8;

      &.warn {
        background: rgba(245, 158, 11, 0.16);
        color: #fbbf24;
      }
      &.ok {
        background: rgba(16, 185, 129, 0.16);
        color: #34d399;
      }
      &.bad {
        background: rgba(239, 68, 68, 0.16);
        color: #f87171;
      }
    }

    .info-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
      margin-bottom: 20px;

      .info-card {
        background: rgba(255, 255, 255, 0.03);
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 8px;
        padding: 14px;

        .box-title {
          font-size: 0.85rem;
          font-weight: 600;
          color: #94a3b8;
          display: flex;
          align-items: center;
          gap: 6px;
          margin-bottom: 10px;
          text-transform: uppercase;
          letter-spacing: 0.5px;

          mat-icon {
            font-size: 18px;
            width: 18px;
            height: 18px;
          }
        }

        .row {
          display: flex;
          justify-content: space-between;
          font-size: 0.85rem;
          margin-bottom: 8px;

          &:last-child {
            margin-bottom: 0;
          }

          .lbl {
            opacity: 0.7;
          }

          .val-code {
            font-family: monospace;
            font-size: 0.8rem;
          }

          .stk {
            color: #38bdf8;
            font-family: monospace;
          }

          .money {
            color: #34d399;
          }

          .kyc-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            color: #10b981;
            font-weight: 600;

            mat-icon {
              font-size: 16px;
              width: 16px;
              height: 16px;
            }
          }
        }
      }
    }

    .cccd-section {
      background: rgba(15, 23, 42, 0.6);
      border: 1px dashed rgba(255, 255, 255, 0.15);
      border-radius: 10px;
      padding: 16px;
      margin-bottom: 20px;

      .section-title {
        font-size: 0.85rem;
        font-weight: 600;
        color: #e2e8f0;
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 12px;

        mat-icon {
          color: #38bdf8;
        }
      }

      .cccd-cards {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
      }

      .cccd-mock {
        border-radius: 8px;
        padding: 12px;
        height: 140px;
        position: relative;
        overflow: hidden;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);

        &.front {
          background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
          border: 1px solid rgba(56, 189, 248, 0.3);

          .cccd-header {
            display: flex;
            align-items: center;
            gap: 8px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.1);
            padding-bottom: 6px;

            .flag {
              font-size: 18px;
            }

            .header-text {
              .c-title {
                font-size: 0.55rem;
                font-weight: 700;
                color: #fbbf24;
                letter-spacing: 0.2px;
              }
              .c-sub {
                font-size: 0.5rem;
                opacity: 0.7;
              }
            }
          }

          .cccd-body {
            display: flex;
            gap: 12px;
            align-items: center;
            margin-top: 4px;

            .avatar-placeholder {
              width: 44px;
              height: 52px;
              background: rgba(255, 255, 255, 0.1);
              border-radius: 4px;
              display: flex;
              align-items: center;
              justify-content: center;

              mat-icon {
                font-size: 32px;
                width: 32px;
                height: 32px;
                opacity: 0.5;
              }
            }

            .cccd-details {
              font-size: 0.7rem;

              .c-num {
                color: #e2e8f0;
                strong {
                  color: #38bdf8;
                }
              }
              .c-name {
                font-weight: 700;
                font-size: 0.75rem;
                color: #fff;
                margin: 2px 0;
                text-transform: uppercase;
              }
              .c-meta {
                opacity: 0.7;
                font-size: 0.65rem;
              }
            }
          }

          .watermark-ok {
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 0.6rem;
            font-weight: 700;
            color: #34d399;
            background: rgba(16, 185, 129, 0.15);
            padding: 2px 6px;
            border-radius: 4px;
            width: fit-content;

            mat-icon {
              font-size: 12px;
              width: 12px;
              height: 12px;
            }
          }
        }

        &.back {
          background: linear-gradient(135deg, #111827 0%, #1e293b 100%);
          border: 1px solid rgba(255, 255, 255, 0.15);

          .chip-graphic {
            display: flex;
            align-items: center;
            gap: 8px;

            .sim-chip {
              width: 28px;
              height: 20px;
              background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
              border-radius: 3px;
            }
            .chip-text {
              font-size: 0.6rem;
              font-weight: 600;
              color: #fbbf24;
            }
          }

          .fingerprint-box {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 0.6rem;
            opacity: 0.8;
            margin: 6px 0;

            mat-icon {
              font-size: 18px;
              width: 18px;
              height: 18px;
              color: #38bdf8;
            }
          }

          .mrz-line {
            font-family: monospace;
            font-size: 0.55rem;
            letter-spacing: 1px;
            opacity: 0.5;
            background: rgba(0, 0, 0, 0.3);
            padding: 4px;
            border-radius: 3px;
          }
        }
      }
    }

    .reject-reason-box {
      background: rgba(239, 68, 68, 0.1);
      border-left: 4px solid #ef4444;
      padding: 10px 14px;
      border-radius: 4px;
      font-size: 0.85rem;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 16px;
    }

    .dialog-actions {
      margin-top: 16px;
      gap: 10px;
    }
  `],
})
export class CardDetailDialogComponent {
  readonly data: CardDetailDialogData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<CardDetailDialogComponent>);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  busy = false;

  close(): void {
    this.dialogRef.close();
  }

  approve(): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.adminApproveCard(this.data.card.id).subscribe({
      next: (card) => {
        this.busy = false;
        this.toast.success(
          this.i18n.instant('ADMIN.CARDS_APPROVED', { last4: card.maskedPan?.slice(-4) ?? '' }),
        );
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.busy = false;
        this.toast.error(resolveHttpErrorMessage(err, this.i18n));
      },
    });
  }

  reject(): void {
    this.dialog
      .open(RejectCardDialogComponent, { data: this.data.card, width: '440px' })
      .afterClosed()
      .subscribe((rejected) => {
        if (rejected) {
          this.dialogRef.close(true);
        }
      });
  }
}
