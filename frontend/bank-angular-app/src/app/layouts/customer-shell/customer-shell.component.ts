import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { AuthActions } from '../../store/auth/auth.actions';
import { selectUsername } from '../../store/auth/auth.selectors';
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
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './customer-shell.component.html',
  styleUrl: './customer-shell.component.scss',
})
export class CustomerShellComponent {
  private readonly store = inject(Store);
  username$ = this.store.select(selectUsername);

  logout(): void {
    this.store.dispatch(AuthActions.logout());
  }
}
