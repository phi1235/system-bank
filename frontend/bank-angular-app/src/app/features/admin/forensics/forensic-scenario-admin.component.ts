import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { ForensicReplayScenario } from '../../../core/models/domain.model';
import { BankApiService } from '../../../core/services/bank-api.service';
import { PERMISSIONS } from '../../../core/services/rbac.util';
import { ToastService } from '../../../core/services/toast.service';
import { resolveHttpErrorMessage } from '../../../core/utils/http-error.util';
import { selectHasPermission } from '../../../store/auth/auth.selectors';
import { take } from 'rxjs';

@Component({
  selector: 'app-forensic-scenario-admin', standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './forensic-scenario-admin.component.html',
  styleUrl: './forensic-scenario-admin.component.scss',
})
export class ForensicScenarioAdminComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly store = inject(Store);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  readonly canAdmin$ = this.store.select(selectHasPermission(PERMISSIONS.FORENSICS_ADMIN));
  @Output() navigateToCase = new EventEmitter<string>();
  scenarios: ForensicReplayScenario[] = [];
  engines: string[] = [];
  faultTypes: string[] = [];
  busy = false;
  draft = { scenarioId: '', title: '', engineKey: '', sourceIncidentId: '',
    sourceEvidenceRef: '', definition: '{\n  "schemaVersion": 1,\n  "faults": []\n}', sanitized: true };

  ngOnInit(): void {
    this.canAdmin$.pipe(take(1)).subscribe(can => { if (can) this.load(); });
  }

  load(): void {
    this.api.forensicReplayScenarios(true).subscribe({
      next: items => this.scenarios = items,
      error: error => this.error(error),
    });
    this.api.forensicReplayScenarioEngines().subscribe({
      next: engines => { this.engines = engines; this.draft.engineKey ||= engines[0] || ''; },
      error: error => this.error(error),
    });
    this.api.forensicReplayScenarioFaultTypes().subscribe({
      next: faultTypes => this.faultTypes = faultTypes,
      error: error => this.error(error),
    });
  }

  create(): void {
    let definition: Record<string, unknown>;
    try { definition = JSON.parse(this.draft.definition); }
    catch { this.toast.error(this.i18n.instant('FORENSICS.INVALID_EVIDENCE_JSON')); return; }
    this.busy = true;
    this.api.createForensicReplayScenario({ ...this.draft, definition }).subscribe({
      next: () => { this.busy = false; this.draft.scenarioId = ''; this.draft.title = ''; this.load(); },
      error: error => { this.busy = false; this.error(error); },
    });
  }

  confirm(item: ForensicReplayScenario): void {
    this.busy = true;
    this.api.confirmForensicReplayScenario(item.scenarioId, item.version).subscribe({
      next: () => { this.busy = false; this.load(); },
      error: error => { this.busy = false; this.error(error); },
    });
  }

  download(item: ForensicReplayScenario): void {
    const artifact = {
      scenarioId: item.scenarioId,
      sourceIncidentId: item.sourceIncidentId,
      sourceEvidenceRef: item.sourceEvidenceRef,
      sanitized: item.sanitized,
      status: item.status,
      confirmedBy: item.confirmedBy,
      confirmedAt: item.confirmedAt,
      definition: item.definition,
    };
    const url = URL.createObjectURL(new Blob(
      [JSON.stringify(artifact, null, 2)],
      { type: 'application/json' },
    ));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${item.scenarioId}.scenario.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private error(error: HttpErrorResponse): void {
    this.toast.error(resolveHttpErrorMessage(error, this.i18n));
  }
}
