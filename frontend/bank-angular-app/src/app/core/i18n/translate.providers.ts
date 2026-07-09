import { HttpClient } from '@angular/common/http';
import { EnvironmentProviders, importProvidersFrom, inject, provideAppInitializer } from '@angular/core';
import { TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { firstValueFrom } from 'rxjs';

export const LANG_STORAGE_KEY = 'bs_lang';

export function httpTranslateLoader(http: HttpClient): TranslateHttpLoader {
  return new TranslateHttpLoader(http, '/i18n/', '.json');
}

export function provideAppI18n(): EnvironmentProviders[] {
  return [
    importProvidersFrom(
      TranslateModule.forRoot({
        defaultLanguage: 'vi',
        loader: {
          provide: TranslateLoader,
          useFactory: httpTranslateLoader,
          deps: [HttpClient],
        },
      }),
    ),
    provideAppInitializer(() => {
      const translate = inject(TranslateService);
      translate.addLangs(['vi', 'en']);
      translate.setDefaultLang('vi');
      const saved = localStorage.getItem(LANG_STORAGE_KEY);
      const lang = saved === 'en' || saved === 'vi' ? saved : 'vi';
      return firstValueFrom(translate.use(lang));
    }),
  ];
}
