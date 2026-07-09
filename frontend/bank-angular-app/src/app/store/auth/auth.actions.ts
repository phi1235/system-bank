import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { LoginRequest, MeResponse, RegisterRequest, TokenResponse } from '../../core/models/auth.model';

export const AuthActions = createActionGroup({
  source: 'Auth',
  events: {
    'Bootstrap': emptyProps(),
    'Bootstrap Success': props<{ user: MeResponse }>(),
    'Bootstrap Failure': emptyProps(),

    'Login': props<{ request: LoginRequest; admin?: boolean }>(),
    'Login Success': props<{ tokens: TokenResponse; admin?: boolean }>(),
    'Login Mfa Required': props<{ mfaToken: string; admin?: boolean }>(),
    'Login Failure': props<{ error: string }>(),

    'Register': props<{ request: RegisterRequest }>(),
    'Register Success': props<{ username: string }>(),
    'Register Failure': props<{ error: string }>(),

    'Verify Mfa': props<{ mfaToken: string; code: string; admin?: boolean }>(),
    'Verify Mfa Success': props<{ tokens: TokenResponse; admin?: boolean }>(),

    'Load Me': emptyProps(),
    'Load Me Success': props<{ user: MeResponse }>(),
    'Load Me Failure': emptyProps(),

    'Logout': emptyProps(),
    'Logout Done': emptyProps(),

    'Clear Error': emptyProps(),
  },
});
