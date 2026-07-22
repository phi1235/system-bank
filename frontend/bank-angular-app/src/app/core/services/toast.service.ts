import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';

/**
 * App-wide toast notifications — always top-right (modern dashboard pattern).
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslateService);

  success(message: string): void {
    this.open(message, this.i18n.instant('COMMON.OK'), 3500, ['app-toast', 'toast-success']);
  }

  error(message: string): void {
    this.open(message, this.i18n.instant('COMMON.CLOSE'), 5000, ['app-toast', 'toast-error']);
  }

  info(message: string): void {
    this.open(message, this.i18n.instant('COMMON.OK'), 3000, ['app-toast', 'toast-info']);
  }

  private open(
    message: string,
    action: string,
    duration: number,
    panelClass: string[],
  ): void {
    this.snack.open(message, action, {
      duration,
      horizontalPosition: 'right',
      verticalPosition: 'top',
      panelClass,
      politeness: 'polite',
    });
  }
}
