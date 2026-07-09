import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AccountsState } from './accounts.reducer';

export const selectAccountsState = createFeatureSelector<AccountsState>('accounts');
export const selectAccounts = createSelector(selectAccountsState, (s) => s.list);
export const selectAccountsLoading = createSelector(selectAccountsState, (s) => s.loading);
export const selectTotalBalance = createSelector(selectAccounts, (list) =>
  list.filter((a) => a.status === 'ACTIVE').reduce((sum, a) => sum + Number(a.balance || 0), 0),
);
