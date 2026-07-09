import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  LoginRequest,
  LoginResponse,
  MeResponse,
  MfaSetupResponse,
  RegisterRequest,
  TokenResponse,
} from '../models/auth.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly api = inject(ApiService);

  register(body: RegisterRequest): Observable<{ userId: string; username: string }> {
    return this.api.post('/auth/register', body);
  }

  login(body: LoginRequest): Observable<LoginResponse> {
    return this.api.post('/auth/login', body);
  }

  refresh(refreshToken: string): Observable<TokenResponse> {
    return this.api.post('/auth/refresh', { refreshToken });
  }

  logout(): Observable<unknown> {
    return this.api.post('/auth/logout', {});
  }

  me(): Observable<MeResponse> {
    return this.api.get('/auth/me');
  }

  verifyMfa(mfaToken: string, code: string): Observable<TokenResponse> {
    return this.api.post('/auth/mfa/verify', { mfaToken, code });
  }

  mfaSetup(): Observable<MfaSetupResponse> {
    return this.api.post('/auth/mfa/setup', {});
  }

  mfaEnable(code: string): Observable<{ mfaEnabled: boolean }> {
    return this.api.post('/auth/mfa/enable', { code });
  }
}
