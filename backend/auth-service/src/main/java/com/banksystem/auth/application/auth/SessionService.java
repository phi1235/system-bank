package com.banksystem.auth.application.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;

import com.banksystem.auth.api.dto.AuthDtos.SessionResponse;
import java.util.List;
import java.util.UUID;

public interface SessionService {
  String resolveRefreshJti(String refreshToken);
  void trackRefreshSession(String refreshJti, UUID userId, String ip, String userAgent, long ttlSeconds);
  List<SessionResponse> listSessions(UUID userId, String currentRefreshJti);
  void revoke(UUID userId, String sessionId, String currentRefreshJti);
  int revokeOthers(UUID userId, String currentRefreshJti);
  int revokeAll(UUID userId);
  void forget(UUID userId, String refreshJti);
}
