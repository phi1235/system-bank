import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslateService);

  success(message: string): void {
    this.snack.open(message, this.i18n.instant('COMMON.OK'), {
      duration: 3500,
      horizontalPosition: 'end',
      verticalPosition: 'top',
      panelClass: ['toast-success'],
    });
  }

  error(message: string): void {
    this.snack.open(message, this.i18n.instant('COMMON.CLOSE'), {
      duration: 5000,
      horizontalPosition: 'end',
      verticalPosition: 'top',
      panelClass: ['toast-error'],
    });
  }

  info(message: string): void {
    this.snack.open(message, this.i18n.instant('COMMON.OK'), {
      duration: 3000,
      horizontalPosition: 'end',
      verticalPosition: 'top',
    });
  }
}
