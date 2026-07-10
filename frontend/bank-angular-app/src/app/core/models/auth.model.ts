export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  mfaRequired?: boolean;
  mfaToken?: string | null;
  mustChangePassword?: boolean;
}

export interface LoginResponse extends TokenResponse {
  mfaRequired: boolean;
  mfaToken: string | null;
  mustChangePassword?: boolean;
}

export interface MeResponse {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  permissions: string[];
  mfaEnabled: boolean;
  staff?: boolean;
  mustChangePassword?: boolean;
  enabled?: boolean;
}

export interface PasswordResetTicket {
  ticketId: string;
  username: string;
  emailMasked: string;
  channel: string;
  status: string;
  requesterNote?: string | null;
  rejectReason?: string | null;
  createdAt?: string | null;
  fulfilledAt?: string | null;
  rejectedAt?: string | null;
}

export interface PasswordResetFulfillResult {
  ticketId: string;
  status: string;
  channel: string;
  deliveryMasked: string;
  message: string;
}

export interface MfaSetupResponse {
  otpauthUri: string;
  secret: string;
}
