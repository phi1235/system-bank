import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthSession,
  LoginRequest,
  LoginResponse,
  MeResponse,
  MfaSetupResponse,
  PasswordResetFulfillResult,
  PasswordResetTicket,
  RegisterRequest,
  TokenResponse,
} from '../models/auth.model';
import { PageResponse } from '../models/api.model';
import { ApiService } from './api.service';
import { TokenService } from './token.service';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly api = inject(ApiService);
  private readonly tokens = inject(TokenService);

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
    const refreshToken = this.tokens.getRefreshToken();
    return this.api.post('/auth/logout', refreshToken ? { refreshToken } : {});
  }

  listSessions(): Observable<AuthSession[]> {
    return this.api.get('/auth/sessions');
  }

  revokeSession(id: string): Observable<{ status: string }> {
    return this.api.delete(`/auth/sessions/${encodeURIComponent(id)}`);
  }

  revokeOtherSessions(): Observable<{ status: string; revoked: number }> {
    return this.api.post('/auth/sessions/revoke-others', {});
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

  /** Guest: open password-reset ticket */
  createPasswordResetTicket(body: {
    usernameOrEmail: string;
    channel?: string;
    note?: string;
  }): Observable<PasswordResetTicket> {
    return this.api.post('/auth/password-reset/tickets', body);
  }

  /** Authenticated: change password (clears mustChangePassword) */
  changePassword(body: { currentPassword: string; newPassword: string }): Observable<{ status: string }> {
    return this.api.post('/auth/password/change', body);
  }

  listPasswordResetTickets(
    status?: string,
    page = 0,
    size = 20,
  ): Observable<PageResponse<PasswordResetTicket>> {
    return this.api.get('/admin/password-reset/tickets', { status, page, size });
  }

  fulfillPasswordReset(ticketId: string): Observable<PasswordResetFulfillResult> {
    return this.api.post(`/admin/password-reset/tickets/${ticketId}/fulfill`, {});
  }

  /** Blind reset from user management row (no ticket UI). */
  resetUserPassword(userId: string, channel = 'EMAIL'): Observable<PasswordResetFulfillResult> {
    return this.api.post(`/admin/users/${userId}/password-reset?channel=${encodeURIComponent(channel)}`, {});
  }

  rejectPasswordReset(ticketId: string, reason?: string): Observable<PasswordResetTicket> {
    return this.api.post(`/admin/password-reset/tickets/${ticketId}/reject`, { reason });
  }

  lockUser(userId: string, reason?: string): Observable<{ status: string }> {
    return this.api.post(`/admin/users/${userId}/lock`, { reason });
  }

  unlockUser(userId: string): Observable<{ status: string }> {
    return this.api.post(`/admin/users/${userId}/unlock`, {});
  }
}
