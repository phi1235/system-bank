import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Corporation } from './corporate.models';
import { CorporateApiService } from './services/corporate-api.service';

@Component({
  selector: 'app-corporate-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatFormFieldModule,
    MatSelectModule,
    TranslateModule,
  ],
  templateUrl: './corporate-shell.component.html',
  styleUrl: './corporate-shell.component.scss',
})
export class CorporateShellComponent implements OnInit {
  private readonly api = inject(CorporateApiService);

  corporations: Corporation[] = [];
  selectedCorpId = '';
  selectedCorp: Corporation | null = null;
  inboxCount = 0;

  ngOnInit() {
    this.loadCorporations();
    this.loadInboxCount();
  }

  loadCorporations() {
    this.api.getMyCorporations().subscribe({
      next: (list) => {
        this.corporations = list;
        if (list.length > 0) {
          const stored = localStorage.getItem('selected_corp_id');
          const found = list.find((c) => c.id === stored) || list[0];
          this.selectedCorpId = found.id;
          this.selectedCorp = found;
          localStorage.setItem('selected_corp_id', found.id);
        }
      },
    });
  }

  onCorpChange(id: string) {
    this.selectedCorpId = id;
    this.selectedCorp = this.corporations.find((c) => c.id === id) || null;
    localStorage.setItem('selected_corp_id', id);
  }

  loadInboxCount() {
    this.api.getInbox().subscribe({
      next: (tasks) => {
        this.inboxCount = tasks.filter((t) => t.status === 'ACTIVE').length;
      },
    });
  }
}
