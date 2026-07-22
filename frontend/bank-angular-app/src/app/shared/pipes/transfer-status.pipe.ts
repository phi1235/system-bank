import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

/** Maps transfer/saga status codes to localized labels; falls back to raw code. */
@Pipe({ name: 'transferStatus', standalone: true, pure: false })
export class TransferStatusPipe implements PipeTransform {
  private readonly i18n = inject(TranslateService);

  transform(status: string | null | undefined): string {
    if (!status) {
      return '';
    }
    const key = `TRANSFER_STATUS.${status}`;
    const translated = this.i18n.instant(key);
    return translated && translated !== key ? translated : status;
  }
}
