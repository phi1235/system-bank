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
