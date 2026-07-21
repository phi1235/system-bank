import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { PageResponse } from '../../core/models/api.model';
import { Transfer, TransferRequest } from '../../core/models/domain.model';

export const TransfersActions = createActionGroup({
  source: 'Transfers',
  events: {
    'Create': props<{ request: TransferRequest; idempotencyKey: string }>(),
    'Create Success': props<{ transfer: Transfer }>(),
    'Create Failure': props<{ error: string }>(),
    'Load History': props<{
      page?: number;
      size?: number;
      status?: string;
      from?: string;
      to?: string;
    }>(),
    'Load History Success': props<{ page: PageResponse<Transfer> }>(),
    'Load History Failure': props<{ error: string }>(),
    'Clear Status': emptyProps(),
  },
});
