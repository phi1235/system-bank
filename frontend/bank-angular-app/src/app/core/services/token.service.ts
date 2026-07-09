import { Injectable } from '@angular/core';

const ACCESS = 'bs_access';
const REFRESH = 'bs_refresh';

@Injectable({ providedIn: 'root' })
export class TokenService {
  private memoryAccess: string | null = null;

  getAccessToken(): string | null {
    if (this.memoryAccess) return this.memoryAccess;
    const s = sessionStorage.getItem(ACCESS);
    this.memoryAccess = s;
    return s;
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH);
  }

  setTokens(access: string, refresh: string): void {
    this.memoryAccess = access;
    sessionStorage.setItem(ACCESS, access);
    sessionStorage.setItem(REFRESH, refresh);
  }

  clear(): void {
    this.memoryAccess = null;
    sessionStorage.removeItem(ACCESS);
    sessionStorage.removeItem(REFRESH);
  }

  hasToken(): boolean {
    return !!this.getAccessToken();
  }
}
