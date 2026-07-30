import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-form-control-error',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    <ng-container *ngIf="shouldShowError()">
      <small class="text-danger d-block mt-1">
        <ng-container *ngIf="control?.hasError('required')">
          {{ 'VALIDATION.REQUIRED' | translate }}
        </ng-container>

        <ng-container *ngIf="control?.hasError('minlength')">
          {{ 'VALIDATION.MIN_LENGTH' | translate: { requiredLength: control?.getError('minlength')?.requiredLength } }}
        </ng-container>

        <ng-container *ngIf="control?.hasError('maxlength')">
          {{ 'VALIDATION.MAX_LENGTH' | translate: { requiredLength: control?.getError('maxlength')?.requiredLength } }}
        </ng-container>

        <ng-container *ngIf="control?.hasError('pattern')">
          {{ 'VALIDATION.PATTERN' | translate }}
        </ng-container>

        <ng-container *ngIf="control?.hasError('email')">
          {{ 'VALIDATION.EMAIL' | translate }}
        </ng-container>

        <ng-container *ngIf="control?.hasError('min')">
          {{ 'VALIDATION.MIN' | translate: { min: control?.getError('min')?.min } }}
        </ng-container>
      </small>
    </ng-container>
  `,
  styles: [
    `
      .text-danger {
        color: #dc3545;
        font-size: 0.85rem;
      }
    `,
  ],
})
export class FormControlErrorComponent {
  @Input() control: AbstractControl | null = null;

  shouldShowError(): boolean {
    return !!(this.control && this.control.invalid && (this.control.dirty || this.control.touched));
  }
}
