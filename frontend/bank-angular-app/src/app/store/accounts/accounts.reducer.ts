import { createReducer, on } from '@ngrx/store';
import { Account } from '../../core/models/domain.model';
import { AccountsActions } from './accounts.actions';

export interface AccountsState {
  list: Account[];
  loading: boolean;
  error: string | null;
}

export const initialAccountsState: AccountsState = {
  list: [],
  loading: false,
  error: null,
};

export const accountsReducer = createReducer(
  initialAccountsState,
  on(AccountsActions.load, AccountsActions.open, (s) => ({ ...s, loading: true, error: null })),
  on(AccountsActions.loadSuccess, (s, { accounts }) => ({
    ...s,
    list: accounts,
    loading: false,
  })),
  on(AccountsActions.openSuccess, (s, { account }) => ({
    ...s,
    list: [account, ...s.list],
    loading: false,
  })),
  on(AccountsActions.loadFailure, AccountsActions.openFailure, (s, { error }) => ({
    ...s,
    loading: false,
    error,
  })),
);
