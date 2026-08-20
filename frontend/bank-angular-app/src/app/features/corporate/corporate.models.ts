export interface Corporation {
  id: string;
  taxId: string;
  companyName: string;
  shortName?: string;
  kycStatus: string;
  status: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CorporateMember {
  id: string;
  corporateId: string;
  userId: string;
  status: string;
  roles: string[];
  joinedAt: string;
  expiresAt?: string;
}

export interface CorporateAccount {
  id: string;
  corporateId: string;
  accountId: string;
  accountNumber: string;
  accountName?: string;
  currency: string;
  balance: number;
  isPrimary: boolean;
  status: string;
  dailyPayoutLimit?: number;
  createdAt: string;
}

export interface ApprovalStepTemplate {
  id?: string;
  stepOrder: number;
  stepName: string;
  requiredRole: string; // CHECKER, CFO, CHAIRMAN, etc.
  minApprovals: number;
  authMethod: string; // STANDARD, TOTP_STEPUP, DIGITAL_SIGNATURE_CA
  deadlineHours?: number;
}

export interface ApprovalTier {
  id?: string;
  tierName: string;
  minAmount: number;
  maxAmount?: number;
  priorityOrder: number;
  steps: ApprovalStepTemplate[];
}

export interface ApprovalPolicy {
  id: string;
  corporateId: string;
  policyName: string;
  versionNumber: number;
  status: string; // DRAFT, ACTIVE, RETIRED
  currency: string;
  allowSelfApproval: boolean;
  requireRoleSeparation: boolean;
  effectiveFrom?: string;
  effectiveTo?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  tiers: ApprovalTier[];
}

export interface PayoutBatch {
  id: string;
  corporateId: string;
  sourceAccountId: string;
  sourceAccountNumber: string;
  batchName: string;
  totalItems: number;
  validItems: number;
  invalidItems: number;
  processedItems: number;
  successfulItems: number;
  failedItems: number;
  totalAmount: number;
  totalFee: number;
  currency: string;
  status: string; // DRAFT, UPLOADED, VALIDATING, VALIDATION_FAILED, READY_FOR_SUBMISSION, PENDING_APPROVAL, RETURNED, REJECTED, APPROVED, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED
  fileSha256: string;
  policyId?: string;
  policyVersion?: number;
  canonicalPayloadHash?: string;
  holdId?: string;
  createdBy: string;
  submittedBy?: string;
  submittedAt?: string;
  approvedAt?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PayoutItem {
  id: string;
  batchId: string;
  rowNumber: number;
  employeeCode?: string;
  beneficiaryName: string;
  accountNumber: string;
  bankCode: string;
  amount: number;
  feeAmount: number;
  currency: string;
  description?: string;
  employeeEmail?: string;
  payrollPeriod?: string;
  status: string; // VALID, INVALID, QUEUED, CLAIMED, SUCCESS, FAILED_FINAL, RETRY_WAIT, MANUAL_REVIEW
  validationError?: string;
  transactionId?: string;
  idempotencyKey?: string;
  executionVersion: number;
  retryCount: number;
  failureReason?: string;
  receiptArtifactId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalTask {
  id: string;
  instanceId: string;
  batchId: string;
  batchName: string;
  corporateId: string;
  stepOrder: number;
  stepName: string;
  requiredRole: string;
  minApprovals: number;
  currentApprovals: number;
  authMethod: string;
  status: string; // PENDING, ACTIVE, APPROVED, REJECTED, RETURNED, SKIPPED
  deadline?: string;
  totalAmount: number;
  totalItems: number;
  currency: string;
  createdAt: string;
}

export interface ApprovalAction {
  id: string;
  taskId: string;
  batchId: string;
  actorId: string;
  actorRole: string;
  action: string;
  comments?: string;
  actionTimestamp: string;
}

export interface ApprovalInstanceDetail {
  id: string;
  batchId: string;
  policyVersion: number;
  totalSteps: number;
  currentStep: number;
  status: string;
  tasks: ApprovalTask[];
  actions: ApprovalAction[];
}

export interface ReceiptArtifact {
  id: string;
  corporateId: string;
  batchId: string;
  itemId?: string;
  artifactType: string;
  fileKey: string;
  fileSha256: string;
  fileSizeBytes: number;
  emailSent: boolean;
  emailSentAt?: string;
  createdAt: string;
}

export interface SimulatedPlan {
  policyId: string;
  policyVersion: number;
  policyName: string;
  tierName: string;
  minAmount: number;
  maxAmount?: number;
  steps: {
    stepOrder: number;
    stepName: string;
    requiredRole: string;
    minApprovals: number;
    authMethod: string;
    eligibleUserIds: string[];
  }[];
}

export interface BatchProgress {
  batchId: string;
  status: string;
  totalItems: number;
  processedItems: number;
  successfulItems: number;
  failedItems: number;
  progressPercentage: number;
  processedAmount: number;
  totalAmount: number;
}
