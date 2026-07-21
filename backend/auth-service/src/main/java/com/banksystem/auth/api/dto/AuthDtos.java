package com.banksystem.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {
  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank @Size(min = 3, max = 50) String username,
      @NotBlank @Email String email,
      @NotBlank String password,
      String fullName
  ) {}

  public record RegisterResponse(String userId, String username) {}

  public record LoginRequest(
      @NotBlank String username,
      @NotBlank String password
  ) {}

  public record LoginResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      Long expiresIn,
      boolean mfaRequired,
      String mfaToken,
      boolean mustChangePassword
  ) {
    public static LoginResponse tokens(TokenResponse t, boolean mustChangePassword) {
      return new LoginResponse(t.accessToken(), t.refreshToken(), t.tokenType(), t.expiresIn(),
          false, null, mustChangePassword);
    }

    public static LoginResponse mfaRequired(String mfaToken) {
      return new LoginResponse(null, null, null, null, true, mfaToken, false);
    }
  }

  public record TokenResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn,
      boolean mfaRequired,
      boolean mustChangePassword
  ) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {}

  public record MfaEnableRequest(@NotBlank String code) {}

  public record MfaSetupResponse(String otpauthUri, String secret) {}

  public record UserMeResponse(
      String userId,
      String username,
      String email,
      List<String> roles,
      List<String> permissions,
      boolean mfaEnabled,
      boolean staff,
      boolean mustChangePassword,
      boolean enabled
  ) {}

  /** Active refresh session (device/browser) for the signed-in user. */
  public record SessionResponse(
      String id,
      String ip,
      String userAgent,
      Instant createdAt,
      Instant expiresAt,
      boolean current
  ) {}
}
