package com.banksystem.auth.application.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.domain.auth.UserEntity;
import java.util.UUID;

public interface MfaService {
  record MfaSetupResult(String otpauthUri, String secret) {}

  MfaSetupResult setup(UserEntity user);
  void enable(UserEntity user, String code);
  boolean verifyUserCode(UUID userId, String code);
  String qrPngBase64(String otpauthUri);
}
