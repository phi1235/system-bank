import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, exhaustMap, map, tap } from 'rxjs/operators';
import { BankApiService } from '../../core/services/bank-api.service';
import { ToastService } from '../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../core/utils/http-error.util';
import { AccountsActions } from './accounts.actions';

@Injectable()
export class AccountsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.load),
      exhaustMap(() =>
        this.api.listAccounts().pipe(
          map((accounts) => AccountsActions.loadSuccess({ accounts })),
          catchError((err) => {
            const message =
              resolveHttpErrorMessage(err, this.i18n) ||
              this.i18n.instant('CUSTOMER.LOAD_ACCOUNTS_FAIL');
            return of(AccountsActions.loadFailure({ error: message }));
          }),
        ),
      ),
    ),
  );

  loadFailureToast$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AccountsActions.loadFailure),
        tap(({ error }) => {
          if (error) {
            this.toast.error(error);
          }
        }),
      ),
    { dispatch: false },
  );

  open$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.open),
      exhaustMap(({ accountType }) =>
        this.api.openAccount(accountType || 'PAYMENT').pipe(
          map((account) => {
            this.toast.success(this.i18n.instant('CUSTOMER.OPEN_OK'));
            return AccountsActions.openSuccess({ account });
          }),
          catchError((err) => {
            const message =
              resolveHttpErrorMessage(err, this.i18n) ||
              this.i18n.instant('CUSTOMER.OPEN_FAIL');
            return of(AccountsActions.openFailure({ error: message }));
          }),
        ),
      ),
    ),
  );

  openFailureToast$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AccountsActions.openFailure),
        tap(({ error }) => {
          if (error) {
            this.toast.error(error);
          }
        }),
      ),
    { dispatch: false },
  );
}
