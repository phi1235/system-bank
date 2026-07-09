# Frontend conventions — Bank Angular App

## Component structure (bắt buộc)

Mỗi component UI **tách 3 file**:

```
feature/
  foo.component.ts      # logic only — không inline template/styles
  foo.component.html
  foo.component.scss
```

- Cấm `template: \`...\`` / `styles: [...]` (trừ stub cực ngắn đã migrate).
- Standalone + `templateUrl` + `styleUrl`.

## i18n (bắt buộc cho user-facing text)

- Runtime: **@ngx-translate** (`public/i18n/vi.json`, `en.json`).
- Template: `{{ 'KEY.PATH' | translate }}` hoặc `[title]="'KEY' | translate"`.
- TypeScript toast/confirm: `this.i18n.instant('KEY')`.
- **Không** hardcode tiếng Việt/Anh trong HTML/TS cho copy UI.
- API error message từ backend có thể hiển thị raw (server message).

### Thêm ngôn ngữ

1. Thêm `public/i18n/xx.json`  
2. `translate.addLangs(['vi','en','xx'])` trong `translate.providers.ts`  
3. Thêm option trong `lang-switcher`

### Switch language

`app-lang-switcher` — lưu `localStorage` key `bs_lang`.

## Import shared

```ts
import { PageHeaderComponent } from '.../page-header/page-header.component';
import { LoadingComponent } from '.../loading/loading.component';
import { TranslateModule } from '@ngx-translate/core';
```

## Verify (low RAM)

```bash
npm run lint   # tsc --noEmit
```
