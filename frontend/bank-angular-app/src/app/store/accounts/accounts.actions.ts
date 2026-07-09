import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Account } from '../../core/models/domain.model';

export const AccountsActions = createActionGroup({
  source: 'Accounts',
  events: {
    'Load': emptyProps(),
    'Load Success': props<{ accounts: Account[] }>(),
    'Load Failure': props<{ error: string }>(),
    'Open': props<{ accountType?: string }>(),
    'Open Success': props<{ account: Account }>(),
    'Open Failure': props<{ error: string }>(),
  },
});
