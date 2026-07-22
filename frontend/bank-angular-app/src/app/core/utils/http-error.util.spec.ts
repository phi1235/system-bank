import { HttpErrorResponse } from '@angular/common/http';
import { resolveHttpErrorMessage } from './http-error.util';

function mockI18n(map: Record<string, string>) {
  return {
    instant: (key: string, params?: Record<string, string>) => {
      const raw = map[key] ?? key;
      if (!params) {
        return raw;
      }
      return raw.replace(/\{\{(\w+)\}\}/g, (_, k: string) => params[k] ?? '');
    },
  } as any;
}

describe('resolveHttpErrorMessage', () => {
  const i18n = mockI18n({
    'ERRORS.GENERIC': 'System error',
    'ERRORS.SERVER': 'System is temporarily unavailable. Please try again later.',
    'ERRORS.NETWORK': 'Cannot connect to the server. Check your network.',
    'ERRORS.NOT_FOUND': 'The requested resource was not found.',
    'ERRORS.BAD_REQUEST': 'Invalid request. Please check your input.',
    'ERRORS.FORBIDDEN': 'You do not have permission for this action.',
    'ERRORS.TIMEOUT': 'The request timed out. Please try again.',
    'ERRORS.CONFLICT': 'This action conflicts with the current state.',
    'ERRORS.TOO_MANY_REQUESTS': 'Too many requests. Please wait a moment.',
    'ERRORS.UNAUTHORIZED': 'Please sign in again.',
    'ERRORS.BENEFICIARY_EXISTS': 'Already in list',
  });

  it('maps business error code to i18n', () => {
    const err = new HttpErrorResponse({
      status: 409,
      statusText: 'OK',
      error: { error: { code: 'BENEFICIARY_EXISTS', message: 'exists' } },
    });
    expect(resolveHttpErrorMessage(err, i18n)).toBe('Already in list');
  });

  it('never shows 500: OK style junk', () => {
    const err = new HttpErrorResponse({
      status: 500,
      statusText: 'OK',
      url: '/api/v1/x',
      error: null,
    });
    const msg = resolveHttpErrorMessage(err, i18n);
    expect(msg).toBe('System is temporarily unavailable. Please try again later.');
    expect(msg).not.toMatch(/500/);
    expect(msg).not.toMatch(/\bOK\b/);
  });

  it('uses SERVER for 5xx even when message is Http failure response', () => {
    const err = new HttpErrorResponse({
      status: 500,
      statusText: 'OK',
      error: 'Http failure response for http://x: 500 OK',
    });
    expect(resolveHttpErrorMessage(err, i18n)).toBe(
      'System is temporarily unavailable. Please try again later.',
    );
  });

  it('uses NETWORK for status 0', () => {
    const err = new HttpErrorResponse({
      status: 0,
      statusText: 'Unknown Error',
      error: new ProgressEvent('error'),
    });
    expect(resolveHttpErrorMessage(err, i18n)).toBe(
      'Cannot connect to the server. Check your network.',
    );
  });

  it('prefers BE message when no code mapping', () => {
    const err = new HttpErrorResponse({
      status: 400,
      statusText: 'OK',
      error: { error: { code: 'SOME_UNKNOWN', message: 'Nickname too long' } },
    });
    expect(resolveHttpErrorMessage(err, i18n)).toBe('Nickname too long');
  });
});
