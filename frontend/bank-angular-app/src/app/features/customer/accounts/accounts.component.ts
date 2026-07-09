import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import { selectAccounts, selectAccountsLoading } from '../../../store/accounts/accounts.selectors';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatTableModule, MatChipsModule, PageHeaderComponent, LoadingComponent, MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AccountsComponent implements OnInit {
  private readonly store = inject(Store);
  accounts$ = this.store.select(selectAccounts);
  loading$ = this.store.select(selectAccountsLoading);
  cols = ['accountNumber', 'accountType', 'balance', 'status'];

  ngOnInit(): void {
    this.store.dispatch(AccountsActions.load());
  }

  open(type = 'PAYMENT'): void {
    this.store.dispatch(AccountsActions.open({ accountType: type }));
  }
}
