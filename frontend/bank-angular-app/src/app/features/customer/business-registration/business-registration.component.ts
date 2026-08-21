import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BankApiService } from '../../../core/services/bank-api.service';
import { BusinessContextService } from '../../../core/services/business-context.service';
import { BusinessOrganization, RegisterBusinessRequest } from '../../../core/models/domain.model';

@Component({
  selector: 'app-business-registration',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './business-registration.component.html',
  styleUrl: './business-registration.component.scss',
})
export class BusinessRegistrationComponent implements OnInit {
  private readonly bankApi = inject(BankApiService);
  private readonly businessContext = inject(BusinessContextService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  existingOrgs: BusinessOrganization[] = [];
  loading = false;
  submitting = false;
  error: string | null = null;
  successRegisteredOrg: BusinessOrganization | null = null;

  form: RegisterBusinessRequest = {
    legalName: '',
    taxNumber: '',
    contactEmail: '',
    contactPhone: '',
    address: '',
    representativeName: '',
    industry: '',
  };

  ngOnInit(): void {
    this.loadMyBusinesses();
  }

  loadMyBusinesses(): void {
    this.loading = true;
    this.bankApi.listMyOrganizations().subscribe({
      next: (orgs: BusinessOrganization[]) => {
        this.existingOrgs = orgs || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  submitRegistration(): void {
    if (!this.form.legalName.trim() || !this.form.taxNumber.trim()) {
      return;
    }

    this.submitting = true;
    this.error = null;

    this.bankApi.registerBusiness(this.form).subscribe({
      next: (res: BusinessOrganization) => {
        this.submitting = false;
        this.successRegisteredOrg = res;
        this.businessContext.loadOrganizations().subscribe();
        this.loadMyBusinesses();
      },
      error: (err: any) => {
        this.submitting = false;
        this.error = err?.error?.message || this.translate.instant('CUSTOMER.BUSINESS_REG.ERROR');
      },
    });
  }

  goToB2bPortal(orgId: string): void {
    const org = this.existingOrgs.find((o) => o.id === orgId) || this.successRegisteredOrg;
    if (org) {
      this.businessContext.selectOrganization(org);
    }
    this.router.navigate(['/business/dashboard']);
  }
}

