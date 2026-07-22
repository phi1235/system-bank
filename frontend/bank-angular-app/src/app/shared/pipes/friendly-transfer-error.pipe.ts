import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import {
  parseTransferError,
  transferErrorI18nKey,
} from '../../core/utils/transfer-error.util';

/**
 * Maps raw transfer failureReason / API error message to a localized customer string.
 * Falls back to the original text when no i18n key exists.
 */
@Pipe({ name: 'friendlyTransferError', standalone: true, pure: false })
export class FriendlyTransferErrorPipe implements PipeTransform {
  private readonly i18n = inject(TranslateService);

  transform(reason: string | null | undefined, apiCode?: string | null): string {
    const parsed = parseTransferError(reason, apiCode);
    if (!parsed.raw && !parsed.code) {
      return '';
    }
    const key = transferErrorI18nKey(parsed.code);
    if (key) {
      const translated = this.i18n.instant(key);
      if (translated && translated !== key) {
        return translated;
      }
    }
    return parsed.detail || parsed.raw;
  }
}
