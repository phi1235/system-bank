import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { BankApiService } from '../../../core/services/bank-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { selectHasPermission, selectUser } from '../../../store/auth/auth.selectors';
import { CustomerProfile } from '../../../core/models/domain.model';
import { MfaSetupResponse } from '../../../core/models/auth.model';
import { PERMISSIONS } from '../../../core/services/rbac.util';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly bank = inject(BankApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly store = inject(Store);
  user$ = this.store.select(selectUser);
  canEdit$ = this.store.select(selectHasPermission(PERMISSIONS.IB_PROFILE_EDIT));
  canMfa$ = this.store.select(selectHasPermission(PERMISSIONS.IB_PROFILE_MFA));
  profile: CustomerProfile | null = null;
  needsCreate = false;
  mfaSetup: MfaSetupResponse | null = null;
  loading = false;

  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    phone: [''],
    email: [''],
    nationalId: [''],
    address: [''],
  });

  mfaForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.bank.getProfile().subscribe({
      next: (p) => {
        this.profile = p;
        this.needsCreate = false;
        this.form.patchValue({
          fullName: p.fullName || '',
          phone: p.phone || '',
          address: p.address || '',
          email: p.email || '',
        });
        this.loading = false;
      },
      error: () => {
        this.needsCreate = true;
        this.loading = false;
      },
    });
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    if (this.needsCreate) {
      this.bank.createProfile({
        fullName: v.fullName,
        phone: v.phone || undefined,
        email: v.email || undefined,
        nationalId: v.nationalId || undefined,
        address: v.address || undefined,
      }).subscribe({
        next: (p) => {
          this.profile = p;
          this.needsCreate = false;
          this.toast.success(this.i18n.instant('CUSTOMER.PROFILE_CREATE_OK'));
        },
      });
    } else {
      this.bank.updateProfile({
        fullName: v.fullName,
        phone: v.phone || undefined,
        address: v.address || undefined,
      }).subscribe({
        next: (p) => {
          this.profile = p;
          this.toast.success(this.i18n.instant('CUSTOMER.PROFILE_UPDATE_OK'));
        },
      });
    }
  }

  startMfa(): void {
    this.authApi.mfaSetup().subscribe({
      next: (s) => {
        this.mfaSetup = s;
        this.toast.info(this.i18n.instant('CUSTOMER.MFA_SETUP_HINT'));
      },
    });
  }

  enableMfa(): void {
    if (this.mfaForm.invalid) return;
    this.authApi.mfaEnable(this.mfaForm.controls.code.value).subscribe({
      next: () => {
        this.toast.success(this.i18n.instant('CUSTOMER.MFA_ENABLED_OK'));
        this.mfaSetup = null;
      },
    });
  }
}
