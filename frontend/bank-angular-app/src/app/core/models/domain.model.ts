export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  accountType: string;
  currency: string;
  balance: number;
  status: string;
}

export interface CustomerProfile {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  nationalIdMasked: string | null;
  kycStatus: string;
  address: string | null;
}

export interface Transfer {
  transactionId: string;
  status: string;
  fromAccountId: string;
  toAccountId: string | null;
  toAccountNumber: string | null;
  amount: number;
  /** Fee charged on source in addition to amount (skeleton; may be 0). */
  feeAmount?: number;
  currency: string;
  description: string | null;
  failureReason: string | null;
  createdAt: string;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountNumber: string;
  amount: number;
  description?: string;
  currency?: string;
}

/** Account ledger line (DEBIT/CREDIT), not transfer-order history. */
export interface LedgerEntry {
  id: string;
  accountId: string;
  entryType: 'DEBIT' | 'CREDIT' | string;
  amount: number;
  signedAmount: number;
  referenceId: string | null;
  description: string | null;
  createdAt: string;
}

export interface Beneficiary {
  id: string;
  nickname: string;
  accountNumber: string;
  accountId: string | null;
  currency: string;
  active: boolean;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  actorUserId: string | null;
  action: string;
  resourceType: string;
  resourceId: string;
  ip: string | null;
  metadata: string | null;
  createdAt: string;
}

/** Outbox event for ops (Kafka publish lifecycle). */
export interface OutboxEvent {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  status: 'PENDING' | 'PUBLISHED' | 'DEAD' | string;
  attemptCount: number;
  nextAttemptAt: string | null;
  createdAt: string;
  publishedAt: string | null;
  lastError: string | null;
}

export interface OutboxCounts {
  pending: number;
  published: number;
  dead: number;
}
