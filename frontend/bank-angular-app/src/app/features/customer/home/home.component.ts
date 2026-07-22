import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { EnumLabelPipe } from '../../../shared/pipes/enum-label.pipe';
import { TransferStatusPipe } from '../../../shared/pipes/transfer-status.pipe';
import { AccountMaskPipe } from '../../../shared/pipes/account-mask.pipe';
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import { selectAccounts, selectAccountsLoading, selectTotalBalance } from '../../../store/accounts/accounts.selectors';
import { selectUsername } from '../../../store/auth/auth.selectors';
import { TransfersActions } from '../../../store/transfers/transfers.actions';
import { selectTransferHistory } from '../../../store/transfers/transfers.selectors';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    EnumLabelPipe,
    TransferStatusPipe,
    AccountMaskPipe,
    TranslateModule,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly store = inject(Store);
  username$ = this.store.select(selectUsername);
  accounts$ = this.store.select(selectAccounts);
  total$ = this.store.select(selectTotalBalance);
  loading$ = this.store.select(selectAccountsLoading);
  recent$ = this.store.select(selectTransferHistory);

  ngOnInit(): void {
    this.store.dispatch(AccountsActions.load());
    this.store.dispatch(TransfersActions.loadHistory({ page: 0, size: 5 }));
  }
}
