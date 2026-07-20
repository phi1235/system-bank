import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  get<T>(path: string, params?: Record<string, string | number | boolean | undefined>): Observable<T> {
    return this.http
      .get<ApiResponse<T>>(this.url(path), { params: this.toParams(params) })
      .pipe(map((r) => this.unwrap(r)));
  }

  post<T>(path: string, body?: unknown, headers?: HttpHeaders | Record<string, string>): Observable<T> {
    return this.http
      .post<ApiResponse<T>>(this.url(path), body ?? {}, { headers })
      .pipe(map((r) => this.unwrap(r)));
  }

  put<T>(path: string, body?: unknown): Observable<T> {
    return this.http.put<ApiResponse<T>>(this.url(path), body ?? {}).pipe(map((r) => this.unwrap(r)));
  }

  patch<T>(path: string, body?: unknown): Observable<T> {
    return this.http.patch<ApiResponse<T>>(this.url(path), body ?? {}).pipe(map((r) => this.unwrap(r)));
  }

  delete<T = void>(path: string): Observable<T> {
    return this.http.delete<ApiResponse<T> | null>(this.url(path), { observe: 'body' }).pipe(
      map((r) => {
        // 204 No Content or empty body
        if (r == null) {
          return undefined as T;
        }
        // Some endpoints return ApiResponse envelope; others are empty.
        if (typeof r === 'object' && 'success' in (r as object)) {
          return this.unwrap(r as ApiResponse<T>);
        }
        return r as T;
      }),
    );
  }

  private url(path: string): string {
    return path.startsWith('http') ? path : `${this.base}${path.startsWith('/') ? path : '/' + path}`;
  }

  private unwrap<T>(r: ApiResponse<T>): T {
    if (!r.success || r.data === undefined) {
      const msg = r.error?.message || 'Request failed';
      throw Object.assign(new Error(msg), { apiError: r.error, code: r.error?.code });
    }
    return r.data;
  }

  private toParams(params?: Record<string, string | number | boolean | undefined>): HttpParams | undefined {
    if (!params) return undefined;
    let p = new HttpParams();
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== '') {
        p = p.set(k, String(v));
      }
    }
    return p;
  }
}
