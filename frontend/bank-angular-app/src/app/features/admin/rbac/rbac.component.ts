import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-rbac',
  standalone: true,
  imports: [MatCardModule, MatTableModule, PageHeaderComponent,
    TranslateModule,
  ],
  templateUrl: './rbac.component.html',
  styleUrl: './rbac.component.scss',
})
export class AdminRbacComponent {
  cols = ['role', 'desc', 'perms'];
  rows = [
    { role: 'SUPER_ADMIN', desc: 'Toàn quyền BO', perms: 'all' },
    { role: 'OPS_ADMIN', desc: 'Vận hành TK/GD', perms: 'customers:read, accounts:freeze, tx:monitor' },
    { role: 'KYC_OFFICER', desc: 'Định danh', perms: 'customers:kyc_decide' },
    { role: 'COMPLIANCE', desc: 'Tuân thủ', perms: 'audit:read, risk:cases' },
    { role: 'SUPPORT', desc: 'CSKH', perms: 'customers:read' },
    { role: 'AUDITOR', desc: 'Kiểm toán', perms: 'audit:read' },
    { role: 'ADMIN (MVP)', desc: 'Backend hiện tại', perms: 'ROLE_ADMIN flat — roadmap permissions[]' },
  ];
}
