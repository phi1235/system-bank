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
}

export interface LoginResponse extends TokenResponse {
  mfaRequired: boolean;
  mfaToken: string | null;
}

export interface MeResponse {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  permissions: string[];
  mfaEnabled: boolean;
  staff?: boolean;
}

export interface MfaSetupResponse {
  otpauthUri: string;
  secret: string;
}
