import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { AuthActions } from '../../../store/auth/auth.actions';
import { selectAuthError, selectAuthLoading, selectMfaToken } from '../../../store/auth/auth.selectors';
import { LangSwitcherComponent } from '../../../shared/components/lang-switcher/lang-switcher.component';

@Component({
  selector: 'app-mfa',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    TranslateModule,
    LangSwitcherComponent,
  ],
  templateUrl: './mfa.component.html',
  styleUrl: './mfa.component.scss',
})
export class MfaComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  loading$ = this.store.select(selectAuthLoading);
  error$ = this.store.select(selectAuthError);
  mfaToken$ = this.store.select(selectMfaToken);
  admin = false;

  form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  ngOnInit(): void {
    this.admin = this.route.snapshot.queryParamMap.get('admin') === '1';
  }

  submit(mfaToken: string | null): void {
    if (!mfaToken || this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.store.dispatch(AuthActions.verifyMfa({
      mfaToken,
      code: this.form.controls.code.value,
      admin: this.admin,
    }));
  }
}
