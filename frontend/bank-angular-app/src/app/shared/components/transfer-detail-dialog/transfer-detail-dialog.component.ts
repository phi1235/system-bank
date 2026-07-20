import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { TransferDetail } from '../../../core/models/domain.model';
import { MoneyVndPipe } from '../../pipes/money-vnd.pipe';

export interface TransferDetailDialogData {
  detail: TransferDetail;
}

@Component({
  selector: 'app-transfer-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, TranslateModule, MoneyVndPipe],
  template: `
    <h2 mat-dialog-title>{{ 'TRANSFER_DETAIL.TITLE' | translate }}</h2>
    <mat-dialog-content>
      @if (detail; as d) {
        <div class="meta">
          <div class="row">
            <span class="k">{{ 'COMMON.STATUS' | translate }}</span>
            <span
              class="chip"
              [class.ok]="d.transfer.status === 'COMPLETED'"
              [class.warn]="d.transfer.status === 'PENDING' || d.transfer.status === 'COMPENSATED'"
              [class.bad]="d.transfer.status === 'FAILED'"
              >{{ d.transfer.status }}</span
            >
          </div>
          <div class="row">
            <span class="k">{{ 'COMMON.AMOUNT' | translate }}</span>
            <span class="money">{{ d.transfer.amount | moneyVnd: d.transfer.currency }}</span>
          </div>
          <div class="row">
            <span class="k">{{ 'CUSTOMER.FEE' | translate }}</span>
            <span class="money">{{ (d.transfer.feeAmount || 0) | moneyVnd: d.transfer.currency }}</span>
          </div>
          <div class="row">
            <span class="k">{{ 'TRANSFER_DETAIL.TO' | translate }}</span>
            <span>{{ d.transfer.toAccountNumber || '—' }}</span>
          </div>
          <div class="row">
            <span class="k">{{ 'COMMON.TIME' | translate }}</span>
            <span>{{ d.transfer.createdAt | date: 'medium' }}</span>
          </div>
          <div class="row">
            <span class="k">{{ 'TRANSFER_DETAIL.ID' | translate }}</span>
            <code>{{ d.transfer.transactionId }}</code>
          </div>
          @if (d.transfer.failureReason) {
            <div class="row fail">
              <span class="k">{{ 'TRANSFER_DETAIL.FAILURE' | translate }}</span>
              <span>{{ d.transfer.failureReason }}</span>
            </div>
          }
          @if (d.transfer.description) {
            <div class="row">
              <span class="k">{{ 'CUSTOMER.HISTORY_DESC' | translate }}</span>
              <span>{{ d.transfer.description }}</span>
            </div>
          }
        </div>

        <h3 class="steps-title">{{ 'TRANSFER_DETAIL.STEPS' | translate }}</h3>
        @if (d.steps.length === 0) {
          <p class="empty">{{ 'TRANSFER_DETAIL.NO_STEPS' | translate }}</p>
        } @else {
          <ol class="steps">
            @for (s of d.steps; track s.id) {
              <li>
                <div class="step-head">
                  <strong>{{ s.step }}</strong>
                  <span
                    class="chip sm"
                    [class.ok]="s.status === 'SUCCESS'"
                    [class.warn]="s.status !== 'SUCCESS' && s.status !== 'FAILED'"
                    [class.bad]="s.status === 'FAILED'"
                    >{{ s.status }}</span
                  >
                </div>
                <div class="step-meta">{{ s.createdAt | date: 'short' }}</div>
                @if (s.detail) {
                  <div class="step-detail">{{ s.detail }}</div>
                }
              </li>
            }
          </ol>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" type="button" mat-dialog-close>
        {{ 'COMMON.CLOSE' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .meta {
        display: grid;
        gap: 0.5rem;
        margin-bottom: 1rem;
      }
      .row {
        display: grid;
        grid-template-columns: 8rem 1fr;
        gap: 0.5rem;
        align-items: start;
        font-size: 0.92rem;
      }
      .row.fail span:last-child {
        color: #b71c1c;
      }
      .k {
        opacity: 0.7;
      }
      .money {
        font-variant-numeric: tabular-nums;
        font-weight: 600;
      }
      .chip {
        display: inline-block;
        padding: 0.12rem 0.45rem;
        border-radius: 999px;
        font-size: 0.75rem;
        font-weight: 600;
        background: #eceff1;
        width: fit-content;
      }
      .chip.sm {
        font-size: 0.7rem;
      }
      .chip.ok {
        background: #e8f5e9;
        color: #1b5e20;
      }
      .chip.warn {
        background: #fff8e1;
        color: #f57f17;
      }
      .chip.bad {
        background: #ffebee;
        color: #b71c1c;
      }
      .steps-title {
        margin: 0 0 0.5rem;
        font-size: 0.95rem;
      }
      .steps {
        margin: 0;
        padding-left: 1.1rem;
        display: grid;
        gap: 0.75rem;
      }
      .step-head {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        flex-wrap: wrap;
      }
      .step-meta {
        font-size: 0.8rem;
        opacity: 0.65;
        margin-top: 0.15rem;
      }
      .step-detail {
        font-size: 0.85rem;
        opacity: 0.85;
        margin-top: 0.2rem;
        word-break: break-word;
      }
      .empty {
        opacity: 0.7;
        margin: 0;
      }
      code {
        font-size: 0.8rem;
        word-break: break-all;
      }
      mat-dialog-content {
        min-width: min(480px, 85vw);
        max-width: 560px;
      }
    `,
  ],
})
export class TransferDetailDialogComponent {
  readonly data = inject<TransferDetailDialogData>(MAT_DIALOG_DATA);
  readonly detail = this.data.detail;
}
