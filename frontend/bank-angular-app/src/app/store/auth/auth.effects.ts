import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, exhaustMap, map, tap } from 'rxjs/operators';
import { AuthApiService } from '../../core/services/auth-api.service';
import { isStaffUser } from '../../core/services/rbac.util';
import { ToastService } from '../../core/services/toast.service';
import { TokenService } from '../../core/services/token.service';
import { AuthActions } from './auth.actions';

@Injectable()
export class AuthEffects {
  private readonly actions$ = inject(Actions);
  private readonly authApi = inject(AuthApiService);
  private readonly tokens = inject(TokenService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  bootstrap$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.bootstrap),
      exhaustMap(() => {
        if (!this.tokens.hasToken()) {
          return of(AuthActions.bootstrapFailure());
        }
        return this.authApi.me().pipe(
          map((user) => AuthActions.bootstrapSuccess({ user })),
          catchError(() => {
            this.tokens.clear();
            return of(AuthActions.bootstrapFailure());
          }),
        );
      }),
    ),
  );

  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.login),
      exhaustMap(({ request, admin }) =>
        this.authApi.login(request).pipe(
          map((res) => {
            if (res.mfaRequired && res.mfaToken) {
              return AuthActions.loginMfaRequired({ mfaToken: res.mfaToken, admin });
            }
            this.tokens.setTokens(res.accessToken, res.refreshToken);
            return AuthActions.loginSuccess({ tokens: res, admin });
          }),
          catchError((err) =>
            of(
              AuthActions.loginFailure({
                error:
                  err?.error?.error?.message ||
                  err?.message ||
                  this.i18n.instant('AUTH.LOGIN_FAILED'),
              }),
            ),
          ),
        ),
      ),
    ),
  );

  loginSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loginSuccess, AuthActions.verifyMfaSuccess),
      exhaustMap(({ admin }) =>
        this.authApi.me().pipe(
          map((user) => {
            const roles = user.roles || [];
            const permissions = user.permissions || [];
            if (admin && !isStaffUser(roles, permissions) && !user.staff) {
              this.tokens.clear();
              this.toast.error(this.i18n.instant('AUTH.NO_ADMIN'));
              return AuthActions.loginFailure({ error: this.i18n.instant('AUTH.NO_ADMIN') });
            }
            return AuthActions.loadMeSuccess({ user });
          }),
          catchError(() => of(AuthActions.loadMeFailure())),
        ),
      ),
    ),
  );

  /** Force change password when mustChangePassword (login + page refresh). */
  forceChangePassword$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loadMeSuccess, AuthActions.bootstrapSuccess),
        tap(({ user }) => {
          if (user.mustChangePassword && !this.router.url.startsWith('/auth/change-password')) {
            this.router.navigateByUrl('/auth/change-password');
          }
        }),
      ),
    { dispatch: false },
  );

  afterMe$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loadMeSuccess),
        tap(({ user }) => {
          if (user.mustChangePassword) {
            return; // handled by forceChangePassword$
          }
          const roles = user.roles || [];
          const permissions = user.permissions || [];
          const staff = !!user.staff || isStaffUser(roles, permissions);
          if (staff && (this.router.url.includes('/admin') || this.router.url.startsWith('/admin/login'))) {
            this.router.navigateByUrl('/admin');
            return;
          }
          if (staff && !roles.includes('CUSTOMER')) {
            this.router.navigateByUrl('/admin');
            return;
          }
          if (this.router.url.startsWith('/admin')) {
            this.router.navigateByUrl('/admin');
          } else if (this.router.url.startsWith('/auth') || this.router.url === '/' || this.router.url.startsWith('/admin/login')) {
            if (roles.includes('CUSTOMER')) {
              this.router.navigateByUrl('/customer/home');
            } else if (staff) {
              this.router.navigateByUrl('/admin');
            }
          }
        }),
      ),
    { dispatch: false },
  );

  mfaRedirect$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loginMfaRequired),
        tap(({ admin }) => {
          this.router.navigate(['/auth/mfa'], { queryParams: { admin: admin ? '1' : '0' } });
        }),
      ),
    { dispatch: false },
  );

  verifyMfa$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.verifyMfa),
      exhaustMap(({ mfaToken, code, admin }) =>
        this.authApi.verifyMfa(mfaToken, code).pipe(
          map((tokens) => {
            this.tokens.setTokens(tokens.accessToken, tokens.refreshToken);
            return AuthActions.verifyMfaSuccess({ tokens, admin });
          }),
          catchError((err) =>
            of(
              AuthActions.loginFailure({
                error: err?.error?.error?.message || this.i18n.instant('AUTH.MFA_INVALID'),
              }),
            ),
          ),
        ),
      ),
    ),
  );

  register$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.register),
      exhaustMap(({ request }) =>
        this.authApi.register(request).pipe(
          map((r) => {
            this.toast.success(this.i18n.instant('AUTH.REGISTER_OK'));
            return AuthActions.registerSuccess({ username: r.username });
          }),
          catchError((err) =>
            of(
              AuthActions.registerFailure({
                error: err?.error?.error?.message || this.i18n.instant('AUTH.REGISTER_FAILED'),
              }),
            ),
          ),
        ),
      ),
    ),
  );

  registerOk$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.registerSuccess),
        tap(() => this.router.navigateByUrl('/auth/login')),
      ),
    { dispatch: false },
  );

  logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logout),
      exhaustMap(() =>
        this.authApi.logout().pipe(
          map(() => AuthActions.logoutDone()),
          catchError(() => of(AuthActions.logoutDone())),
          tap(() => {
            this.tokens.clear();
            this.router.navigateByUrl('/auth/login');
          }),
        ),
      ),
    ),
  );
}
