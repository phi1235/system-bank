import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-admin-placeholder',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, RouterLink, PageHeaderComponent, TranslateModule],
  templateUrl: './placeholder.component.html',
  styleUrl: './placeholder.component.scss',
})
export class AdminPlaceholderComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(TranslateService);
  title = '';
  subtitle = '';

  ngOnInit(): void {
    const d = this.route.snapshot.data;
    this.title = d['titleKey'] ? this.i18n.instant(d['titleKey']) : this.i18n.instant('COMMON.COMING_SOON');
    this.subtitle = d['subtitleKey'] ? this.i18n.instant(d['subtitleKey']) : '';
  }
}
