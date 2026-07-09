import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'accountMask', standalone: true })
export class AccountMaskPipe implements PipeTransform {
  transform(value: string | null | undefined, visible = 4): string {
    if (!value) return '—';
    if (value.length <= visible) return value;
    return '••••' + value.slice(-visible);
  }
}
