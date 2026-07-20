import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { TransfersActions } from '../../../store/transfers/transfers.actions';
import { selectTransferHistory, selectTransferLoading, selectTransferPageMeta } from '../../../store/transfers/transfers.selectors';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
})
export class HistoryComponent implements OnInit {
  private readonly store = inject(Store);
  rows$ = this.store.select(selectTransferHistory);
  loading$ = this.store.select(selectTransferLoading);
  meta$ = this.store.select(selectTransferPageMeta);
  cols = ['createdAt', 'description', 'amount', 'feeAmount', 'status', 'transactionId'];

  ngOnInit(): void {
    this.store.dispatch(TransfersActions.loadHistory({ page: 0, size: 10 }));
  }

  page(e: PageEvent): void {
    this.store.dispatch(TransfersActions.loadHistory({ page: e.pageIndex, size: e.pageSize }));
  }
}
