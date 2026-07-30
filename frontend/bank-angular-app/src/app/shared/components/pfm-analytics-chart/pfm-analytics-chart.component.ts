import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

export interface CashflowMonthlyData {
  month: string;
  income: number;
  expense: number;
}

export interface ExpenseCategoryShare {
  name: string;
  percentage: number;
  color: string;
}

@Component({
  selector: 'app-pfm-analytics-chart',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    <div class="pfm-analytics-card card border-0 shadow-sm p-4 rounded-4">
      <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h5 class="fw-bold mb-1">{{ 'PFM.TITLE' | translate }}</h5>
          <span class="text-muted small">{{ 'PFM.SUBTITLE' | translate }}</span>
        </div>
        <div class="badge bg-light text-dark border px-3 py-2 rounded-pill">
          {{ 'PFM.PERIOD_6M' | translate }}
        </div>
      </div>

      <div class="row">
        <!-- Income vs Expense Bar Chart -->
        <div class="col-md-7 mb-4 mb-md-0">
          <h6 class="fw-bold text-secondary mb-3 small">{{ 'PFM.CASHFLOW_OVERVIEW' | translate }}</h6>
          <div class="chart-bar-container d-flex align-items-end justify-content-between gap-2 border-bottom pb-2">
            <div *ngFor="let item of monthlyData" class="bar-group text-center flex-fill">
              <div class="bars d-flex align-items-end justify-content-center gap-1">
                <div
                  class="bar bar-income bg-success rounded-top"
                  [style.height.px]="(item.income / maxAmount) * 120"
                  [title]="('PFM.INCOME' | translate) + ': ' + item.income"
                ></div>
                <div
                  class="bar bar-expense bg-danger rounded-top"
                  [style.height.px]="(item.expense / maxAmount) * 120"
                  [title]="('PFM.EXPENSE' | translate) + ': ' + item.expense"
                ></div>
              </div>
              <span class="small text-muted mt-2 d-block">{{ item.month }}</span>
            </div>
          </div>
          <div class="d-flex justify-content-center gap-4 mt-3 small">
            <span class="d-flex align-items-center gap-1">
              <span class="legend-box bg-success"></span> {{ 'PFM.INCOME' | translate }}
            </span>
            <span class="d-flex align-items-center gap-1">
              <span class="legend-box bg-danger"></span> {{ 'PFM.EXPENSE' | translate }}
            </span>
          </div>
        </div>

        <!-- Expense Share breakdown -->
        <div class="col-md-5">
          <h6 class="fw-bold text-secondary mb-3 small">{{ 'PFM.EXPENSE_CATEGORIES' | translate }}</h6>
          <div class="category-list d-flex flex-column gap-2">
            <div *ngFor="let cat of categoryShares" class="category-item">
              <div class="d-flex justify-content-between small font-weight-bold mb-1">
                <span>{{ cat.name }}</span>
                <span>{{ cat.percentage }}%</span>
              </div>
              <div class="progress" style="height: 6px;">
                <div
                  class="progress-bar"
                  role="progressbar"
                  [style.width.%]="cat.percentage"
                  [style.background-color]="cat.color"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .chart-bar-container {
        height: 140px;
      }
      .bars {
        height: 120px;
      }
      .bar {
        width: 14px;
        min-height: 4px;
        transition: height 0.3s ease-in-out;
      }
      .legend-box {
        width: 12px;
        height: 12px;
        border-radius: 3px;
        display: inline-block;
      }
    `,
  ],
})
export class PfmAnalyticsChartComponent {
  @Input() monthlyData: CashflowMonthlyData[] = [
    { month: 'T2', income: 25000000, expense: 12000000 },
    { month: 'T3', income: 25000000, expense: 18000000 },
    { month: 'T4', income: 30000000, expense: 15000000 },
    { month: 'T5', income: 28000000, expense: 14000000 },
    { month: 'T6', income: 32000000, expense: 16000000 },
    { month: 'T7', income: 35000000, expense: 19000000 },
  ];

  @Input() categoryShares: ExpenseCategoryShare[] = [
    { name: 'Chuyển khoản & Thanh toán', percentage: 45, color: '#0d6efd' },
    { name: 'Thanh toán Hóa đơn', percentage: 25, color: '#ffc107' },
    { name: 'Gửi Tiết kiệm', percentage: 20, color: '#198754' },
    { name: 'Chi tiêu Thẻ', percentage: 10, color: '#dc3545' },
  ];

  get maxAmount(): number {
    return Math.max(...this.monthlyData.map((d) => Math.max(d.income, d.expense)), 1);
  }
}
