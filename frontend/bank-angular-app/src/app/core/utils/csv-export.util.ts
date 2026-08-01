import { Subscription } from 'rxjs';
import { ExportQueueService } from '../services/export-queue.service';

export function exportToCsv<T extends Record<string, unknown>>(
  filename: string,
  headers: { key: keyof T | string; label: string }[],
  data: T[],
): void {
  if (!data || data.length === 0) return;

  const BOM = '\uFEFF';
  const headerLine = headers.map((h) => `"${h.label.replace(/"/g, '""')}"`).join(',');
  const rows = data.map((item) =>
    headers
      .map((h) => {
        const val = item[h.key as string];
        const str = val !== null && val !== undefined ? String(val) : '';
        return `"${str.replace(/"/g, '""')}"`;
      })
      .join(','),
  );

  const csvContent = BOM + headerLine + '\n' + rows.join('\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

export function exportToCsvWithQueue<T extends Record<string, unknown>>(
  exportQueue: ExportQueueService,
  taskName: string,
  headers: { key: keyof T | string; label: string }[],
  data: T[],
): string {
  const totalRows = data.length;
  const taskId = exportQueue.addTask(taskName, totalRows);

  let currentChunk = 0;
  const chunkSize = Math.max(1, Math.floor(totalRows / 10)); // 10 steps for smooth progress bar

  const processChunk = () => {
    currentChunk++;
    const processed = Math.min(totalRows, currentChunk * chunkSize);
    exportQueue.updateProgress(taskId, processed, totalRows);

    if (processed < totalRows) {
      setTimeout(processChunk, 150); // Simulate progress chunking for smooth UI
    } else {
      const BOM = '\uFEFF';
      const headerLine = headers.map((h) => `"${h.label.replace(/"/g, '""')}"`).join(',');
      const rows = data.map((item) =>
        headers
          .map((h) => {
            const val = item[h.key as string];
            const str = val !== null && val !== undefined ? String(val) : '';
            return `"${str.replace(/"/g, '""')}"`;
          })
          .join(','),
      );

      const csvContent = BOM + headerLine + '\n' + rows.join('\n');
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const filename = `${taskName.toLowerCase().replace(/\s+/g, '_')}_${new Date().toISOString().slice(0, 10)}.csv`;
      exportQueue.completeTask(taskId, blob, filename);
    }
  };

  setTimeout(processChunk, 100);
  return taskId;
}
