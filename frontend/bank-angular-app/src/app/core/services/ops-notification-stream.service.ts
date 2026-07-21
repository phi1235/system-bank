import { Injectable, OnDestroy, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificationItem } from '../models/domain.model';
import { TokenService } from './token.service';

/**
 * Live ops notification stream (admin BO) via SSE.
 * Separate from customer NotificationStreamService so shells can connect independently.
 */
@Injectable({ providedIn: 'root' })
export class OpsNotificationStreamService implements OnDestroy {
  private readonly tokens = inject(TokenService);
  private readonly events$ = new Subject<NotificationItem>();
  private readonly unreadSubject = new BehaviorSubject<number>(0);
  private active = false;
  private intentionalClose = false;
  private abort: AbortController | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly liveEvents$: Observable<NotificationItem> = this.events$.asObservable();
  readonly unreadCount$: Observable<number> = this.unreadSubject.asObservable();

  get unreadCount(): number {
    return this.unreadSubject.value;
  }

  setUnreadCount(n: number): void {
    this.unreadSubject.next(Math.max(0, n));
  }

  bumpUnread(delta = 1): void {
    this.unreadSubject.next(Math.max(0, this.unreadSubject.value + delta));
  }

  connect(): void {
    if (this.active) {
      return;
    }
    this.active = true;
    this.intentionalClose = false;
    void this.open();
  }

  disconnect(): void {
    this.active = false;
    this.intentionalClose = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.abort?.abort();
    this.abort = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.events$.complete();
    this.unreadSubject.complete();
  }

  private async open(): Promise<void> {
    if (!this.active) {
      return;
    }
    const token = this.tokens.getAccessToken();
    if (!token) {
      this.scheduleReconnect(3000);
      return;
    }

    this.abort?.abort();
    const ac = new AbortController();
    this.abort = ac;

    try {
      const res = await fetch(`${environment.apiUrl}/admin/notifications/stream`, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        signal: ac.signal,
        cache: 'no-store',
      });
      if (!res.ok || !res.body) {
        throw new Error(`SSE HTTP ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (this.active && !ac.signal.aborted) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        buffer = this.consumeSse(buffer);
      }
    } catch {
      if (this.intentionalClose || ac.signal.aborted) {
        return;
      }
    }

    if (this.active && !this.intentionalClose) {
      this.scheduleReconnect(2500);
    }
  }

  private consumeSse(buffer: string): string {
    const parts = buffer.split(/\r?\n\r?\n/);
    const rest = parts.pop() ?? '';
    for (const block of parts) {
      this.handleBlock(block);
    }
    return rest;
  }

  private handleBlock(block: string): void {
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const line of block.split(/\r?\n/)) {
      if (!line || line.startsWith(':')) {
        continue;
      }
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    }
    if (eventName !== 'notification' || dataLines.length === 0) {
      return;
    }
    try {
      const item = JSON.parse(dataLines.join('\n')) as NotificationItem;
      if (item?.id) {
        this.events$.next(item);
        if (!item.read) {
          this.bumpUnread(1);
        }
      }
    } catch {
      // ignore malformed
    }
  }

  private scheduleReconnect(ms: number): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    this.reconnectTimer = setTimeout(() => void this.open(), ms);
  }
}
