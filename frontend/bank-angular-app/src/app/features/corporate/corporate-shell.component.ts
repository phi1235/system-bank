import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../core/services/toast.service';
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
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatDialogModule,
    MatSelectModule,
    TranslateModule,
  ],
  templateUrl: './corporate-shell.component.html',
  styleUrl: './corporate-shell.component.scss',
})
export class CorporateShellComponent implements OnInit {
  private readonly api = inject(CorporateApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  corporations: Corporation[] = [];
  selectedCorpId = '';
  selectedCorp: Corporation | null = null;
  inboxCount = 0;

  showCreateCorpModal = false;
  newTaxId = '';
  newCompanyName = '';
  newShortName = '';
  newContactEmail = '';
  newContactPhone = '';
  newAddress = '';
  creatingCorp = false;

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
        } else {
          this.selectedCorpId = '';
          this.selectedCorp = null;
          localStorage.removeItem('selected_corp_id');
        }
      },
    });
  }

  onCorpChange(id: string) {
    if (id === '__CREATE_NEW__') {
      this.openCreateCorpModal();
      return;
    }
    this.selectedCorpId = id;
    this.selectedCorp = this.corporations.find((c) => c.id === id) || null;
    if (this.selectedCorp) {
      localStorage.setItem('selected_corp_id', this.selectedCorp.id);
    }
  }

  openCreateCorpModal() {
    this.newTaxId = '';
    this.newCompanyName = '';
    this.newShortName = '';
    this.newContactEmail = '';
    this.newContactPhone = '';
    this.newAddress = '';
    this.showCreateCorpModal = true;
  }

  closeCreateCorpModal() {
    this.showCreateCorpModal = false;
  }

  submitCreateCorp() {
    if (!this.newTaxId.trim() || !this.newCompanyName.trim()) {
      this.toast.error(this.translate.instant('VALIDATION.REQUIRED'));
      return;
    }
    this.creatingCorp = true;
    this.api
      .createCorporation({
        taxId: this.newTaxId.trim(),
        companyName: this.newCompanyName.trim(),
        shortName: this.newShortName.trim() || undefined,
        contactEmail: this.newContactEmail.trim() || undefined,
        contactPhone: this.newContactPhone.trim() || undefined,
        address: this.newAddress.trim() || undefined,
      })
      .subscribe({
        next: (corp) => {
          this.creatingCorp = false;
          this.showCreateCorpModal = false;
          this.toast.success(this.translate.instant('TOAST.SUCCESS'));
          this.corporations = [...this.corporations, corp];
          this.selectedCorpId = corp.id;
          this.selectedCorp = corp;
          localStorage.setItem('selected_corp_id', corp.id);
        },
        error: (err) => {
          this.creatingCorp = false;
          const msg =
            err?.error?.error?.message ||
            err?.error?.message ||
            (err?.error?.errors ? Object.values(err.error.errors).join(', ') : null) ||
            this.translate.instant('TOAST.ERROR');
          this.toast.error(msg);
        },
      });
  }

  loadInboxCount() {
    this.api.getInbox().subscribe({
      next: (tasks) => {
        this.inboxCount = tasks.filter((t) => t.status === 'ACTIVE').length;
      },
    });
  }
}
