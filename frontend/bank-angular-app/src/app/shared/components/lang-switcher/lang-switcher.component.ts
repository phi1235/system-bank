import { Component, inject } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LANG_STORAGE_KEY } from '../../../core/i18n/translate.providers';

@Component({
  selector: 'app-lang-switcher',
  standalone: true,
  imports: [UpperCasePipe, MatButtonModule, MatIconModule, MatMenuModule, TranslateModule],
  templateUrl: './lang-switcher.component.html',
  styleUrl: './lang-switcher.component.scss',
})
export class LangSwitcherComponent {
  private readonly i18n = inject(TranslateService);
  currentLang = this.i18n.currentLang || this.i18n.defaultLang || 'vi';

  use(lang: 'vi' | 'en'): void {
    this.i18n.use(lang).subscribe(() => {
      this.currentLang = lang;
      localStorage.setItem(LANG_STORAGE_KEY, lang);
    });
  }
}
