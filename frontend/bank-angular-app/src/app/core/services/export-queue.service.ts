import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { TokenService } from './token.service';
import { decodeJwtPayload } from './jwt.util';
import { BankApiService } from './bank-api.service';

export type ExportTaskStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED' | 'ERROR' | 'PAUSED';

export interface ExportColumnHeader {
  key: string;
  label: string;
}

export interface ExportTask {
  id: string;
  name: string;
  status: ExportTaskStatus;
  progress: number; // 0 - 100
  processedRows: number;
  totalRows: number;
  filename: string;
  blob: Blob | null;
  error?: string;
  createdAt: Date;
  subscription?: Subscription;
  executor?: () => void;
  fetcher?: (page: number, size: number) => Observable<any>;
  headers?: ExportColumnHeader[];
  module?: string;
  filters?: any;
  chunkSize?: number;
  accumulatedItems?: any[];
  lastCreatedAt?: string;
}

@Injectable({
  providedIn: 'root',
})
export class ExportQueueService {
  private readonly tokenService = inject(TokenService);
  private readonly bankApi = inject(BankApiService);
  private readonly tasksSubject = new BehaviorSubject<ExportTask[]>([]);
  readonly tasks$ = this.tasksSubject.asObservable();

  get tasks(): ExportTask[] {
    return this.tasksSubject.getValue();
  }

  get activeTasksCount(): number {
    return this.tasks.filter((t) => t.status === 'PROCESSING' || t.status === 'QUEUED').length;
  }

  constructor() {
    this.loadFromStorage();

    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', (event) => {
        const active = this.tasks.some((t) => t.status === 'PROCESSING' || t.status === 'QUEUED');
        if (active) {
          this.saveToStorage();
          event.preventDefault();
          event.returnValue = '';
        }
      });
    }
  }

  private getUserStorageKey(): string {
    const token = this.tokenService.getAccessToken();
    const payload = decodeJwtPayload(token);
    const userId = (payload?.['sub'] || payload?.['userId'] || payload?.['username'] || 'guest') as string;
    return `bank_system_export_queue_${userId}`;
  }

  enqueueChunkedExport<T>(
    name: string,
    totalRows: number,
    fetcher: (page: number, size: number) => Observable<any>,
    headers: ExportColumnHeader[],
    chunkSize = 2000,
    module = 'general',
    filters: any = {},
  ): string {
    const id = `export_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const filename = `${name.toLowerCase().replace(/\s+/g, '_')}_${new Date().toISOString().slice(0, 10)}.csv`;

    const executor = this.buildExecutor(id, totalRows, chunkSize, fetcher, headers, filename);

    const newTask: ExportTask = {
      id,
      name,
      status: 'QUEUED',
      progress: 0,
      processedRows: 0,
      totalRows,
      filename,
      blob: null,
      createdAt: new Date(),
      executor,
      fetcher,
      headers,
      module,
      filters,
      chunkSize,
      accumulatedItems: [],
    };

    this.updateTasks([newTask, ...this.tasks]);
    this.checkAndProcessNext();
    return id;
  }

  private buildExecutor<T>(
    id: string,
    totalRows: number,
    chunkSize: number,
    fetcher: (page: number, size: number) => Observable<any>,
    headers: ExportColumnHeader[],
    filename: string,
  ): () => void {
    return () => {
      const task = this.tasks.find((t) => t.id === id);
      const accumulated: T[] = task && task.accumulatedItems ? [...task.accumulatedItems] : [];
      let lastCreatedAt: string | undefined = task ? task.lastCreatedAt : undefined;
      const startRows = accumulated.length;
      const totalPages = Math.max(1, Math.ceil(totalRows / chunkSize));
      let currentPage = Math.floor(startRows / chunkSize);

      const fetchNextChunk = () => {
        const currentTask = this.tasks.find((t) => t.id === id);
        if (!currentTask || currentTask.status === 'CANCELLED' || currentTask.status === 'PAUSED') return;

        const effectiveFetcher =
          currentTask.module === 'transfers'
            ? (p: number, s: number) =>
                this.bankApi.adminTransfersExportChunks(p, s, { ...currentTask.filters, lastCreatedAt })
            : fetcher;

        const targetPage = currentTask.module === 'transfers' && lastCreatedAt ? 0 : currentPage;

        const sub = effectiveFetcher(targetPage, chunkSize).subscribe({
          next: (items) => {
            const list = Array.isArray(items) ? items : (items as any).items || [];
            accumulated.push(...list);

            if (list.length > 0) {
              const lastItem = list[list.length - 1];
              if (lastItem && (lastItem.createdAt || lastItem.createDate)) {
                lastCreatedAt = lastItem.createdAt || lastItem.createDate;
              }
            }

            this.updateProgress(id, accumulated.length, totalRows, accumulated, lastCreatedAt);

            currentPage++;
            if (currentPage < totalPages && list.length > 0) {
              setTimeout(fetchNextChunk, 30);
            } else {
              const BOM = '\uFEFF';
              const headerLine = headers.map((h) => `"${h.label.replace(/"/g, '""')}"`).join(',');
              const rows = accumulated.map((item: any) =>
                headers
                  .map((h) => {
                    const val = item[h.key];
                    const str = val !== null && val !== undefined ? String(val) : '';
                    return `"${str.replace(/"/g, '""')}"`;
                  })
                  .join(','),
              );

              const csvContent = BOM + headerLine + '\n' + rows.join('\n');
              const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
              this.completeTask(id, blob, filename);
            }
          },
          error: () => {
            this.failTask(id, 'Lỗi kết nối server');
          },
        });

        this.setTaskSubscription(id, sub);
      };

      fetchNextChunk();
    };
  }

  private resolveFetcherForTask(task: ExportTask): ((page: number, size: number) => Observable<any>) | null {
    if (task.fetcher) return task.fetcher;

    if (task.module === 'transfers') {
      return (page: number, size: number) => this.bankApi.adminTransfersExportChunks(page, size, task.filters);
    }
    if (task.module === 'audit') {
      return (page: number, size: number) => this.bankApi.auditLogs(page, size, task.filters);
    }
    if (task.module === 'accounts') {
      return (page: number, size: number) =>
        this.bankApi.adminListAccounts(page, size, task.filters?.q, task.filters?.status, task.filters?.type);
    }

    return null;
  }

  resumeTask(id: string): void {
    const task = this.tasks.find((t) => t.id === id);
    if (!task) return;

    if (!task.executor) {
      const fetcher = this.resolveFetcherForTask(task);
      if (fetcher && task.headers) {
        task.fetcher = fetcher;
        task.executor = this.buildExecutor(
          task.id,
          task.totalRows,
          task.chunkSize || 2000,
          fetcher,
          task.headers,
          task.filename,
        );
      } else {
        this.failTask(id, 'Không thể khôi phục bộ kết nối tác vụ');
        return;
      }
    }

    const list = this.tasks.map((t) => (t.id === id ? { ...t, status: 'QUEUED' as ExportTaskStatus } : t));
    this.updateTasks(list);
    this.checkAndProcessNext();
  }

  addTask(name: string, totalRows = 0, executor?: () => void): string {
    const id = `export_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const newTask: ExportTask = {
      id,
      name,
      status: 'QUEUED',
      progress: 0,
      processedRows: 0,
      totalRows,
      filename: `${name.toLowerCase().replace(/\s+/g, '_')}_${new Date().toISOString().slice(0, 10)}.csv`,
      blob: null,
      createdAt: new Date(),
      executor,
    };

    this.updateTasks([newTask, ...this.tasks]);
    this.checkAndProcessNext();
    return id;
  }

  private checkAndProcessNext(): void {
    const active = this.tasks.find((t) => t.status === 'PROCESSING');
    if (active) {
      return;
    }

    const queuedTasks = this.tasks.filter((t) => t.status === 'QUEUED');
    if (queuedTasks.length === 0) return;

    const nextJob = queuedTasks[queuedTasks.length - 1];
    const list = this.tasks.map((t) => (t.id === nextJob.id ? { ...t, status: 'PROCESSING' as ExportTaskStatus } : t));
    this.updateTasks(list);

    if (nextJob.executor) {
      setTimeout(() => nextJob.executor!(), 50);
    }
  }

  setTaskSubscription(id: string, subscription: Subscription): void {
    const list = this.tasks.map((t) => (t.id === id ? { ...t, subscription } : t));
    this.updateTasks(list);
  }

  updateProgress(
    id: string,
    processedRows: number,
    totalRows?: number,
    accumulatedItems?: any[],
    lastCreatedAt?: string,
  ): void {
    const list = this.tasks.map((t) => {
      if (t.id !== id) return t;
      const total = totalRows ?? t.totalRows;
      const progress = total > 0 ? Math.min(99, Math.round((processedRows / total) * 100)) : Math.min(95, t.progress + 5);
      return {
        ...t,
        processedRows,
        totalRows: total,
        progress,
        accumulatedItems: accumulatedItems ?? t.accumulatedItems,
        lastCreatedAt: lastCreatedAt ?? t.lastCreatedAt,
      };
    });
    this.updateTasks(list);
  }

  completeTask(id: string, blob: Blob, filename?: string): void {
    const list = this.tasks.map((t) => {
      if (t.id !== id) return t;
      return {
        ...t,
        status: 'COMPLETED' as ExportTaskStatus,
        progress: 100,
        blob,
        filename: filename || t.filename,
      };
    });
    this.updateTasks(list);
    this.autoDownload(blob, filename || this.tasks.find((t) => t.id === id)?.filename || 'export.csv');
    this.checkAndProcessNext();
  }

  cancelTask(id: string): void {
    const list = this.tasks.map((t) => {
      if (t.id !== id) return t;
      if (t.subscription) {
        t.subscription.unsubscribe();
      }
      return {
        ...t,
        status: 'CANCELLED' as ExportTaskStatus,
        progress: 0,
      };
    });
    this.updateTasks(list);
    this.checkAndProcessNext();
  }

  failTask(id: string, error: string): void {
    const list = this.tasks.map((t) => {
      if (t.id !== id) return t;
      return {
        ...t,
        status: 'ERROR' as ExportTaskStatus,
        error,
      };
    });
    this.updateTasks(list);
    this.checkAndProcessNext();
  }

  removeTask(id: string): void {
    const list = this.tasks.filter((t) => t.id !== id);
    this.updateTasks(list);
    this.checkAndProcessNext();
  }

  downloadTask(id: string): void {
    const task = this.tasks.find((t) => t.id === id);
    if (task && task.blob) {
      this.autoDownload(task.blob, task.filename);
    }
  }

  public reloadUserTasks(): void {
    this.loadFromStorage();
  }

  public clearTasksOnLogout(): void {
    this.tasksSubject.next([]);
  }

  private updateTasks(list: ExportTask[]): void {
    this.tasksSubject.next(list);
    this.saveToStorage();
  }

  private saveToStorage(): void {
    if (typeof localStorage === 'undefined') return;
    try {
      const key = this.getUserStorageKey();
      const serializable = this.tasks.map((t) => ({
        id: t.id,
        name: t.name,
        status: t.status === 'PROCESSING' || t.status === 'QUEUED' ? 'PAUSED' : t.status,
        progress: t.progress,
        processedRows: t.processedRows,
        totalRows: t.totalRows,
        filename: t.filename,
        createdAt: t.createdAt,
        module: t.module,
        filters: t.filters,
        headers: t.headers,
        chunkSize: t.chunkSize,
        lastCreatedAt: t.lastCreatedAt,
      }));
      localStorage.setItem(key, JSON.stringify(serializable));
    } catch {
      // Ignore storage errors
    }
  }

  private loadFromStorage(): void {
    if (typeof localStorage === 'undefined') return;
    if (!this.tokenService.hasToken()) {
      this.tasksSubject.next([]);
      return;
    }
    try {
      const key = this.getUserStorageKey();
      const raw = localStorage.getItem(key);
      if (!raw) {
        this.tasksSubject.next([]);
        return;
      }
      const parsed: ExportTask[] = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        const restored = parsed.map((t) => ({
          ...t,
          status: (t.status === 'PROCESSING' || t.status === 'QUEUED' ? 'PAUSED' : t.status) as ExportTaskStatus,
          blob: null,
          createdAt: new Date(t.createdAt),
        }));
        this.tasksSubject.next(restored);
      }
    } catch {
      // Ignore parse errors
    }
  }

  private autoDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
