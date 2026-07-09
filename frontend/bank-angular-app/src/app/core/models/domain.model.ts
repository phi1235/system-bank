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
