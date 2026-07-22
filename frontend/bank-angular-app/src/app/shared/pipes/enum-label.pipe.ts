import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

/**
 * Localizes backend enum/code values: `{{ 'ACTIVE' | enumLabel:'ACCOUNT_STATUS' }}`
 * looks up `ACCOUNT_STATUS.ACTIVE`, falls back to the raw code when missing.
 */
@Pipe({ name: 'enumLabel', standalone: true, pure: false })
export class EnumLabelPipe implements PipeTransform {
  private readonly i18n = inject(TranslateService);

  transform(code: string | null | undefined, namespace: string): string {
    if (!code) {
      return '';
    }
    if (!namespace) {
      return code;
    }
    const key = `${namespace}.${code}`;
    const translated = this.i18n.instant(key);
    return translated && translated !== key ? translated : code;
  }
}
