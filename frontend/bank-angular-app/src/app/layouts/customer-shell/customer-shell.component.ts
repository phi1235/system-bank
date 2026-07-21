import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { AuthActions } from '../../store/auth/auth.actions';
import { selectHasPermission, selectUsername } from '../../store/auth/auth.selectors';
import { PERMISSIONS } from '../../core/services/rbac.util';
import { LangSwitcherComponent } from '../../shared/components/lang-switcher/lang-switcher.component';

@Component({
  selector: 'app-customer-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './customer-shell.component.html',
  styleUrl: './customer-shell.component.scss',
})
export class CustomerShellComponent {
  private readonly store = inject(Store);
  username$ = this.store.select(selectUsername);

  canHome$ = this.store.select(selectHasPermission(PERMISSIONS.IB_HOME_VIEW));
  canAccounts$ = this.store.select(selectHasPermission(PERMISSIONS.IB_ACCOUNTS_VIEW));
  canTransfer$ = this.store.select(selectHasPermission(PERMISSIONS.IB_TRANSFER_VIEW));
  canCards$ = this.store.select(selectHasPermission(PERMISSIONS.IB_CARDS_VIEW));
  canWealth$ = this.store.select(selectHasPermission(PERMISSIONS.IB_WEALTH_VIEW));
  canSupport$ = this.store.select(selectHasPermission(PERMISSIONS.IB_SUPPORT_VIEW));
  canProfile$ = this.store.select(selectHasPermission(PERMISSIONS.IB_PROFILE_VIEW));
  canHistory$ = this.store.select(selectHasPermission(PERMISSIONS.IB_HISTORY_VIEW));
  canNotifications$ = this.store.select(selectHasPermission(PERMISSIONS.IB_NOTIFICATIONS_VIEW));

  logout(): void {
    this.store.dispatch(AuthActions.logout());
  }
}
