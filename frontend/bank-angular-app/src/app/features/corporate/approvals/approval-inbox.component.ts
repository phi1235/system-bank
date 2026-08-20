import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../../core/services/toast.service';
import { ApprovalTask } from '../corporate.models';
import { CorporateApiService } from '../services/corporate-api.service';

@Component({
  selector: 'app-approval-inbox',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDialogModule,
    TranslateModule,
  ],
  templateUrl: './approval-inbox.component.html',
  styleUrl: './approval-inbox.component.scss',
})
export class ApprovalInboxComponent implements OnInit {
  private readonly api = inject(CorporateApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  tasks: ApprovalTask[] = [];
  selectedTask: ApprovalTask | null = null;
  challengeData: any = null;

  comments = '';
  authCode = '';
  signatureRef = '';
  rejectReason = '';
  returnReason = '';
  approving = false;

  private currentDialogRef?: MatDialogRef<any>;

  ngOnInit() {
    this.loadInbox();
  }

  loadInbox() {
    this.api.getInbox().subscribe({
      next: (list) => (this.tasks = list.filter((t) => t.status === 'ACTIVE')),
    });
  }

  openApproveDialog(task: ApprovalTask) {
    this.selectedTask = task;
    this.comments = '';
    this.authCode = '';
    this.signatureRef = '';
    this.challengeData = null;

    if (task.authMethod !== 'STANDARD') {
      this.api.createChallenge(task.id).subscribe({
        next: (ch) => {
          this.challengeData = ch;
        },
      });
    }
  }

  executeApprove() {
    if (!this.selectedTask) return;

    this.approving = true;
    this.api
      .approveTask(this.selectedTask.id, {
        comments: this.comments,
        challengeNonce: this.challengeData?.nonce,
        authCode: this.authCode,
        signatureReference: this.signatureRef,
      })
      .subscribe({
        next: () => {
          this.toast.success(this.translate.instant('CORPORATE.APPROVAL_SUCCESS'));
          this.approving = false;
          this.currentDialogRef?.close();
          this.loadInbox();
        },
        error: (err) => {
          this.toast.error(err.message || this.translate.instant('CORPORATE.APPROVAL_ERROR'));
          this.approving = false;
        },
      });
  }

  openRejectDialog(task: ApprovalTask) {
    this.selectedTask = task;
    this.rejectReason = '';
  }

  executeReject() {
    if (!this.selectedTask || !this.rejectReason) return;
    this.api.rejectTask(this.selectedTask.id, this.rejectReason).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('CORPORATE.REJECT_SUCCESS'));
        this.currentDialogRef?.close();
        this.loadInbox();
      },
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.REJECT_ERROR')),
    });
  }

  openReturnDialog(task: ApprovalTask) {
    this.selectedTask = task;
    this.returnReason = '';
  }

  executeReturn() {
    if (!this.selectedTask || !this.returnReason) return;
    this.api.returnTask(this.selectedTask.id, this.returnReason).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('CORPORATE.RETURN_SUCCESS'));
        this.currentDialogRef?.close();
        this.loadInbox();
      },
      error: (err) => this.toast.error(err.message || this.translate.instant('CORPORATE.RETURN_ERROR')),
    });
  }
}
