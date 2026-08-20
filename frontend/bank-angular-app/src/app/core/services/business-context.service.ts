import { Injectable, inject, signal } from '@angular/core';
import { BehaviorSubject, Observable, catchError, map, of, tap } from 'rxjs';
import { BusinessMembership, BusinessOrganization } from '../models/domain.model';
import { BankApiService } from './bank-api.service';

@Injectable({ providedIn: 'root' })
export class BusinessContextService {
  private readonly api = inject(BankApiService);

  private readonly selectedOrgSubject = new BehaviorSubject<BusinessOrganization | null>(null);
  readonly selectedOrg$ = this.selectedOrgSubject.asObservable();

  private readonly membershipSubject = new BehaviorSubject<BusinessMembership | null>(null);
  readonly membership$ = this.membershipSubject.asObservable();

  readonly organizations = signal<BusinessOrganization[]>([]);
  readonly loading = signal<boolean>(false);

  loadOrganizations(): Observable<BusinessOrganization[]> {
    this.loading.set(true);
    return this.api.listMyOrganizations().pipe(
      tap((orgs) => {
        this.organizations.set(orgs);
        this.loading.set(false);
        const current = this.selectedOrgSubject.value;
        if (!current && orgs.length > 0) {
          const savedId = localStorage.getItem('selected_business_org_id');
          const matched = orgs.find((o) => o.id === savedId) || orgs[0];
          this.selectOrganization(matched);
        } else if (current && !orgs.some((o) => o.id === current.id)) {
          if (orgs.length > 0) {
            this.selectOrganization(orgs[0]);
          } else {
            this.selectedOrgSubject.next(null);
            this.membershipSubject.next(null);
          }
        }
      }),
      catchError((err) => {
        this.loading.set(false);
        return of([]);
      })
    );
  }

  selectOrganization(org: BusinessOrganization): void {
    this.selectedOrgSubject.next(org);
    localStorage.setItem('selected_business_org_id', org.id);
    this.api.getMyBusinessMembership(org.id).subscribe({
      next: (mem) => this.membershipSubject.next(mem),
      error: () => this.membershipSubject.next(null),
    });
  }

  getSelectedOrgId(): string | null {
    return this.selectedOrgSubject.value?.id || localStorage.getItem('selected_business_org_id');
  }

  getSelectedOrg(): BusinessOrganization | null {
    return this.selectedOrgSubject.value;
  }
}
