import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TransfersState } from './transfers.reducer';

export const selectTransfersState = createFeatureSelector<TransfersState>('transfers');
export const selectTransferHistory = createSelector(selectTransfersState, (s) => s.history);
export const selectTransferCreating = createSelector(selectTransfersState, (s) => s.creating);
export const selectTransferLoading = createSelector(selectTransfersState, (s) => s.loading);
export const selectLastTransfer = createSelector(selectTransfersState, (s) => s.lastResult);
export const selectTransferError = createSelector(selectTransfersState, (s) => s.error);
export const selectTransferPageMeta = createSelector(selectTransfersState, (s) => ({
  page: s.page,
  totalElements: s.totalElements,
  totalPages: s.totalPages,
}));
