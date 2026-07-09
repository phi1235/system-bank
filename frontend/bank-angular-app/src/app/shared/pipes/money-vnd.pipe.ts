import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'moneyVnd', standalone: true })
export class MoneyVndPipe implements PipeTransform {
  transform(value: number | string | null | undefined, currency = 'VND'): string {
    if (value === null || value === undefined || value === '') return '—';
    const n = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(n)) return String(value);
    return (
      new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(n) +
      (currency ? ` ${currency}` : '')
    );
  }
}
