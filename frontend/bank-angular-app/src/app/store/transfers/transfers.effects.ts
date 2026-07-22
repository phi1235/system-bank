import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, exhaustMap, map } from 'rxjs/operators';
import { BankApiService } from '../../core/services/bank-api.service';
import { ToastService } from '../../core/services/toast.service';
import {
  parseTransferError,
  transferErrorI18nKey,
} from '../../core/utils/transfer-error.util';
import { resolveHttpErrorMessage } from '../../core/utils/http-error.util';
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
              const reason = this.friendlyReason(transfer.failureReason);
              this.toast.info(
                reason
                  ? this.i18n.instant('CUSTOMER.TRANSFER_STATUS_REASON', {
                      status: transfer.status,
                      reason,
                    })
                  : this.i18n.instant('CUSTOMER.TRANSFER_STATUS', {
                      status: transfer.status,
                    }),
              );
            }
            return TransfersActions.createSuccess({ transfer });
          }),
          catchError((err) =>
            of(
              TransfersActions.createFailure({
                error: this.friendlyCreateError(err),
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

  private friendlyCreateError(err: any): string {
    const apiCode = err?.error?.error?.code as string | undefined;
    const apiMsg = err?.error?.error?.message as string | undefined;
    const mapped = this.friendlyReason(apiMsg, apiCode);
    if (mapped) {
      return mapped;
    }
    if (err instanceof HttpErrorResponse) {
      return resolveHttpErrorMessage(err, this.i18n);
    }
    return this.i18n.instant('CUSTOMER.TRANSFER_FAIL');
  }

  private friendlyReason(
    reason: string | null | undefined,
    apiCode?: string | null,
  ): string {
    const parsed = parseTransferError(reason, apiCode);
    if (!parsed.raw && !parsed.code) {
      return '';
    }
    const key = transferErrorI18nKey(parsed.code);
    if (key) {
      const t = this.i18n.instant(key);
      if (t && t !== key) {
        return t;
      }
    }
    return parsed.detail || parsed.raw;
  }

  history$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransfersActions.loadHistory),
      exhaustMap(({ page, size, status, from, to }) =>
        this.api
          .myTransfers(page ?? 0, size ?? 20, {
            status: status || undefined,
            from: from || undefined,
            to: to || undefined,
          })
          .pipe(
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
