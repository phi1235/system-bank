package com.banksystem.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
      String mfaToken
  ) {
    public static LoginResponse tokens(TokenResponse t) {
      return new LoginResponse(t.accessToken(), t.refreshToken(), t.tokenType(), t.expiresIn(),
          false, null);
    }

    public static LoginResponse mfaRequired(String mfaToken) {
      return new LoginResponse(null, null, null, null, true, mfaToken);
    }
  }

  public record TokenResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn,
      boolean mfaRequired
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
      boolean mfaEnabled
  ) {}
}
