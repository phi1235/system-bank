import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

export interface CashflowMonthlyData {
  month: string;
  income: number;
  expense: number;
}

export interface ExpenseCategoryShare {
  nameKey: string;
  percentage: number;
  color: string;
}

@Component({
  selector: 'app-pfm-analytics-chart',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './pfm-analytics-chart.component.html',
  styleUrl: './pfm-analytics-chart.component.scss',
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
    { nameKey: 'PFM.CAT_TRANSFER', percentage: 45, color: '#0d6efd' },
    { nameKey: 'PFM.CAT_BILL', percentage: 25, color: '#ffc107' },
    { nameKey: 'PFM.CAT_DEPOSIT', percentage: 20, color: '#198754' },
    { nameKey: 'PFM.CAT_CARD', percentage: 10, color: '#dc3545' },
  ];

  get maxAmount(): number {
    return Math.max(...this.monthlyData.map((d) => Math.max(d.income, d.expense)), 1);
  }
}
