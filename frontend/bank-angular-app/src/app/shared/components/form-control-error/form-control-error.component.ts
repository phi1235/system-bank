import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-form-control-error',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './form-control-error.component.html',
  styleUrl: './form-control-error.component.scss',
})
export class FormControlErrorComponent {
  @Input() control: AbstractControl | null = null;

  shouldShowError(): boolean {
    return !!(this.control && this.control.invalid && (this.control.dirty || this.control.touched));
  }
}
