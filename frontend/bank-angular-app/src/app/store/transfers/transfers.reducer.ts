import { createReducer, on } from '@ngrx/store';
import { Transfer } from '../../core/models/domain.model';
import { TransfersActions } from './transfers.actions';

export interface TransfersState {
  history: Transfer[];
  page: number;
  totalElements: number;
  totalPages: number;
  creating: boolean;
  loading: boolean;
  lastResult: Transfer | null;
  error: string | null;
}

export const initialTransfersState: TransfersState = {
  history: [],
  page: 0,
  totalElements: 0,
  totalPages: 0,
  creating: false,
  loading: false,
  lastResult: null,
  error: null,
};

export const transfersReducer = createReducer(
  initialTransfersState,
  on(TransfersActions.create, (s) => ({ ...s, creating: true, error: null, lastResult: null })),
  on(TransfersActions.createSuccess, (s, { transfer }) => ({
    ...s,
    creating: false,
    lastResult: transfer,
    history: [transfer, ...s.history],
  })),
  on(TransfersActions.createFailure, (s, { error }) => ({
    ...s,
    creating: false,
    error,
  })),
  on(TransfersActions.loadHistory, (s) => ({ ...s, loading: true, error: null })),
  on(TransfersActions.loadHistorySuccess, (s, { page }) => ({
    ...s,
    loading: false,
    history: page.items || [],
    page: page.page,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
  })),
  on(TransfersActions.loadHistoryFailure, (s, { error }) => ({
    ...s,
    loading: false,
    error,
  })),
  on(TransfersActions.clearStatus, (s) => ({ ...s, lastResult: null, error: null })),
);
