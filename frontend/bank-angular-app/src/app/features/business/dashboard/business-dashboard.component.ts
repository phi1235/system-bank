import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, switchMap, takeUntil } from 'rxjs';
import { BusinessDashboardSummary } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';

@Component({
  selector: 'app-business-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule, TranslateModule],
  templateUrl: './business-dashboard.component.html',
  styleUrl: './business-dashboard.component.scss',
})
export class BusinessDashboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly destroy$ = new Subject<void>();

  summary: BusinessDashboardSummary | null = null;
  loading = true;

  ngOnInit(): void {
    this.businessContext.selectedOrg$
      .pipe(
        takeUntil(this.destroy$),
        switchMap((org) => {
          if (!org) {
            this.loading = false;
            return [];
          }
          this.loading = true;
          return this.api.getBusinessDashboardSummary(org.id);
        })
      )
      .subscribe({
        next: (res) => {
          this.summary = res;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
