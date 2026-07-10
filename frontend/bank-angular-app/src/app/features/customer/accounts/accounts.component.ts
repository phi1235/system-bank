import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { MoneyVndPipe } from '../../../shared/pipes/money-vnd.pipe';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { AccountsActions } from '../../../store/accounts/accounts.actions';
import { selectAccounts, selectAccountsLoading } from '../../../store/accounts/accounts.selectors';
import { selectHasPermission } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatChipsModule,
    PageHeaderComponent,
    LoadingComponent,
    MoneyVndPipe,
    TranslateModule,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AccountsComponent implements OnInit {
  private readonly store = inject(Store);
  accounts$ = this.store.select(selectAccounts);
  loading$ = this.store.select(selectAccountsLoading);
  canOpen$ = this.store.select(selectHasPermission(PERMISSIONS.IB_ACCOUNTS_OPEN));
  cols = ['accountNumber', 'accountType', 'balance', 'status'];

  ngOnInit(): void {
    this.store.dispatch(AccountsActions.load());
  }

  open(type = 'PAYMENT'): void {
    this.store.dispatch(AccountsActions.open({ accountType: type }));
  }
}
