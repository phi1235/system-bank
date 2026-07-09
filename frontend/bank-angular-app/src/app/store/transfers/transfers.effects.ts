import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, exhaustMap, map } from 'rxjs/operators';
import { BankApiService } from '../../core/services/bank-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AccountsActions } from '../accounts/accounts.actions';
import { TransfersActions } from './transfers.actions';

@Injectable()
export class TransfersEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(BankApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  create$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransfersActions.create),
      exhaustMap(({ request, idempotencyKey }) =>
        this.api.transfer(request, idempotencyKey).pipe(
          map((transfer) => {
            if (transfer.status === 'COMPLETED') {
              this.toast.success(this.i18n.instant('CUSTOMER.TRANSFER_OK'));
            } else {
              this.toast.info(
                this.i18n.instant('CUSTOMER.TRANSFER_STATUS', { status: transfer.status }),
              );
            }
            return TransfersActions.createSuccess({ transfer });
          }),
          catchError((err) =>
            of(
              TransfersActions.createFailure({
                error:
                  err?.error?.error?.message ||
                  err?.message ||
                  this.i18n.instant('CUSTOMER.TRANSFER_FAIL'),
              }),
            ),
          ),
        ),
      ),
    ),
  );

  reloadAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransfersActions.createSuccess),
      map(() => AccountsActions.load()),
    ),
  );

  reloadHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransfersActions.createSuccess),
      map(() => TransfersActions.loadHistory({ page: 0, size: 20 })),
    ),
  );

  history$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransfersActions.loadHistory),
      exhaustMap(({ page, size }) =>
        this.api.myTransfers(page ?? 0, size ?? 20).pipe(
          map((p) => {
            // backend may return content or items
            const items = (p as any).items ?? (p as any).content ?? [];
            return TransfersActions.loadHistorySuccess({
              page: {
                items,
                page: (p as any).page ?? (p as any).number ?? 0,
                size: (p as any).size ?? size ?? 20,
                totalElements: (p as any).totalElements ?? items.length,
                totalPages: (p as any).totalPages ?? 1,
              },
            });
          }),
          catchError((err) =>
            of(
              TransfersActions.loadHistoryFailure({
                error: err?.error?.error?.message || this.i18n.instant('CUSTOMER.HISTORY_FAIL'),
              }),
            ),
          ),
        ),
      ),
    ),
  );
}
