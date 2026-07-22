import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

export interface PromptDialogData {
  title: string;
  message?: string;
  label?: string;
  placeholder?: string;
  /** Pre-filled value. */
  initialValue?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** When true, empty value is not allowed. */
  required?: boolean;
  /** When true, confirm button uses warn color. */
  destructive?: boolean;
  /** Optional max length for the input. */
  maxLength?: number;
}

/**
 * Material dialog that collects a single text value (replaces window.prompt).
 * Closes with the string value, or null/undefined when cancelled.
 */
@Component({
  selector: 'app-prompt-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  templateUrl: './prompt-dialog.component.html',
  styleUrl: './prompt-dialog.component.scss',
})
export class PromptDialogComponent {
  readonly data = inject<PromptDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<PromptDialogComponent, string | null>);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    value: [
      this.data.initialValue ?? '',
      this.data.required ? [Validators.required] : [],
    ],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.controls.value.value ?? '';
    this.dialogRef.close(raw.trim());
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
