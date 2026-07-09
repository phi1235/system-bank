import { Component, Input, inject, OnInit } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-loading',
  standalone: true,
  imports: [MatProgressSpinnerModule, TranslateModule],
  templateUrl: './loading.component.html',
  styleUrl: './loading.component.scss',
})
export class LoadingComponent implements OnInit {
  private readonly i18n = inject(TranslateService);
  @Input() label = '';
  @Input() diameter = 40;
  @Input() overlay = true;

  ngOnInit(): void {
    if (!this.label) {
      this.label = this.i18n.instant('COMMON.LOADING');
    }
  }
}
