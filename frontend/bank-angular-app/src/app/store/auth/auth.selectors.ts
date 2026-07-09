import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AuthState } from './auth.reducer';

export const selectAuthState = createFeatureSelector<AuthState>('auth');

export const selectUser = createSelector(selectAuthState, (s) => s.user);
export const selectRoles = createSelector(selectAuthState, (s) => s.roles);
export const selectAuthLoading = createSelector(selectAuthState, (s) => s.loading);
export const selectAuthError = createSelector(selectAuthState, (s) => s.error);
export const selectMfaPending = createSelector(selectAuthState, (s) => s.mfaPending);
export const selectMfaToken = createSelector(selectAuthState, (s) => s.mfaToken);
export const selectIsAuthenticated = createSelector(selectAuthState, (s) => s.authenticated);
export const selectIsAdmin = createSelector(selectRoles, (r) => r.includes('ADMIN'));
export const selectIsCustomer = createSelector(selectRoles, (r) => r.includes('CUSTOMER'));
export const selectUsername = createSelector(selectUser, (u) => u?.username ?? '');
