import { createReducer, on } from '@ngrx/store';
import { MeResponse } from '../../core/models/auth.model';
import { AuthActions } from './auth.actions';

export interface AuthState {
  user: MeResponse | null;
  roles: string[];
  loading: boolean;
  error: string | null;
  mfaToken: string | null;
  mfaPending: boolean;
  adminIntent: boolean;
  authenticated: boolean;
}

export const initialAuthState: AuthState = {
  user: null,
  roles: [],
  loading: false,
  error: null,
  mfaToken: null,
  mfaPending: false,
  adminIntent: false,
  authenticated: false,
};

export const authReducer = createReducer(
  initialAuthState,
  on(AuthActions.bootstrap, AuthActions.login, AuthActions.register, AuthActions.verifyMfa, AuthActions.loadMe, (s) => ({
    ...s,
    loading: true,
    error: null,
  })),
  on(AuthActions.login, (s, { admin }) => ({
    ...s,
    loading: true,
    error: null,
    adminIntent: !!admin,
  })),
  on(AuthActions.loginSuccess, AuthActions.verifyMfaSuccess, (s, { admin }) => ({
    ...s,
    loading: true,
    mfaPending: false,
    mfaToken: null,
    adminIntent: !!admin,
    authenticated: true,
  })),
  on(AuthActions.loginMfaRequired, (s, { mfaToken, admin }) => ({
    ...s,
    loading: false,
    mfaPending: true,
    mfaToken,
    adminIntent: !!admin,
  })),
  on(AuthActions.bootstrapSuccess, AuthActions.loadMeSuccess, (s, { user }) => ({
    ...s,
    loading: false,
    user,
    roles: user.roles || [],
    authenticated: true,
    error: null,
  })),
  on(AuthActions.bootstrapFailure, AuthActions.loadMeFailure, (s) => ({
    ...s,
    loading: false,
    user: null,
    roles: [],
    authenticated: false,
  })),
  on(AuthActions.loginFailure, AuthActions.registerFailure, (s, { error }) => ({
    ...s,
    loading: false,
    error,
  })),
  on(AuthActions.registerSuccess, (s) => ({
    ...s,
    loading: false,
    error: null,
  })),
  on(AuthActions.logoutDone, () => ({ ...initialAuthState })),
  on(AuthActions.clearError, (s) => ({ ...s, error: null })),
);
