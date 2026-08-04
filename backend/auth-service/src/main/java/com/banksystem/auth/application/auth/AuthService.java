package com.banksystem.auth.application.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.api.dto.AuthDtos.*;
import com.banksystem.auth.domain.auth.UserEntity;
import java.util.UUID;

public interface AuthService {
  RegisterResponse register(RegisterRequest req);
  LoginResponse login(LoginRequest req, String ip, String userAgent);
  TokenResponse verifyMfa(MfaVerifyRequest req, String ip, String userAgent);
  TokenResponse refresh(RefreshRequest req, String ip, String userAgent);
  void logout(String accessToken, String refreshToken);
  MfaSetupResponse setupMfa(UUID userId);
  void enableMfa(UUID userId, MfaEnableRequest req);
  UserMeResponse me(UUID userId);
  UserEntity requireUser(UUID userId);
}
