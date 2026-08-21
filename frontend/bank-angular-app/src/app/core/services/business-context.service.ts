import { Injectable, inject, signal } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, map, of, switchMap, take, tap } from 'rxjs';
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

  loadOrganizations(): Observable<BusinessMembership | null> {
    this.loading.set(true);
    return this.api.listMyOrganizations().pipe(
      tap((orgs) => {
        this.organizations.set(orgs || []);
        this.loading.set(false);
      }),
      switchMap((orgs) => {
        if (orgs && orgs.length > 0) {
          const savedId = localStorage.getItem('selected_business_org_id');
          const matched = orgs.find((o) => o.id === savedId) || orgs[0];
          return this.selectOrganization(matched);
        } else {
          this.selectedOrgSubject.next(null);
          this.membershipSubject.next(null);
          return of(null);
        }
      }),
      catchError(() => {
        this.loading.set(false);
        this.membershipSubject.next(null);
        return of(null);
      })
    );
  }

  ensureLoaded(): Observable<BusinessMembership | null> {
    const current = this.membershipSubject.value;
    if (current) {
      return of(current);
    }
    return this.loadOrganizations();
  }

  clear(): void {
    this.selectedOrgSubject.next(null);
    this.membershipSubject.next(null);
    this.organizations.set([]);
    localStorage.removeItem('selected_business_org_id');
  }

  selectOrganization(org: BusinessOrganization): Observable<BusinessMembership | null> {
    this.selectedOrgSubject.next(org);
    localStorage.setItem('selected_business_org_id', org.id);
    return this.api.getMyBusinessMembership(org.id).pipe(
      tap((mem) => this.membershipSubject.next(mem)),
      catchError(() => {
        this.membershipSubject.next(null);
        return of(null);
      })
    );
  }

  getSelectedOrgId(): string | null {
    return this.selectedOrgSubject.value?.id || localStorage.getItem('selected_business_org_id');
  }

  getSelectedOrg(): BusinessOrganization | null {
    return this.selectedOrgSubject.value;
  }

  getCurrentMembership(): BusinessMembership | null {
    return this.membershipSubject.value;
  }

  hasPermission(perm: string): boolean {
    const mem = this.membershipSubject.value;
    if (!mem) return false;
    const role = (mem.businessRole || '').toUpperCase();
    if (role === 'BUSINESS_OWNER' || role === 'OWNER') return true;
    if (mem.permissions?.includes('*')) return true;
    if (mem.permissions?.includes(perm)) return true;

    // Check with/without "business:" prefix
    const stripped = perm.startsWith('business:') ? perm.substring(9) : perm;
    if (mem.permissions?.includes(stripped) || mem.permissions?.includes('business:' + stripped)) return true;

    // Wildcard matching (e.g. "va:*" matches "va:view", "va:create")
    const parts = stripped.split(':');
    if (parts.length >= 2) {
      if (mem.permissions?.includes(`${parts[0]}:*`)) return true;
    }

    // Alias mapping
    if (stripped === 'orders:view' && mem.permissions?.includes('collection:view')) return true;
    if ((stripped === 'orders:manage' || stripped === 'orders:create') &&
        (mem.permissions?.includes('collection:create') || mem.permissions?.includes('collection:manage') || mem.permissions?.includes('collection:edit'))) return true;
    if (stripped === 'settlements:view' && mem.permissions?.includes('transfer:view')) return true;
    if (stripped === 'settlements:execute' && (mem.permissions?.includes('transfer:approve') || mem.permissions?.includes('transfer:create') || mem.permissions?.includes('batch:approve'))) return true;
    if (stripped === 'dashboard:view') return true;
    if (stripped.startsWith('members:') && (mem.permissions?.includes('org:members') || mem.permissions?.includes('org:members:manage') || mem.permissions?.includes('org:members:view'))) return true;
    if (stripped.startsWith('roles:') && (mem.permissions?.includes('org:roles') || mem.permissions?.includes('org:roles:manage') || mem.permissions?.includes('org:roles:view'))) return true;

    return false;
  }

  hasAnyPermission(perms: string[]): boolean {
    if (!perms || perms.length === 0) return true;
    return perms.some((p) => this.hasPermission(p));
  }
}
