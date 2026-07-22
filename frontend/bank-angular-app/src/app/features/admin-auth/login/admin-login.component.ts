import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { AuthActions } from '../../../store/auth/auth.actions';
import { selectAuthError, selectAuthLoading } from '../../../store/auth/auth.selectors';
import { LangSwitcherComponent } from '../../../shared/components/lang-switcher/lang-switcher.component';

@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './admin-login.component.html',
  styleUrl: './admin-login.component.scss',
})
export class AdminLoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  loading$ = this.store.select(selectAuthLoading);
  error$ = this.store.select(selectAuthError);

  form = this.fb.nonNullable.group({
    username: ['admin', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.store.dispatch(AuthActions.login({ request: this.form.getRawValue(), admin: true }));
  }
}
