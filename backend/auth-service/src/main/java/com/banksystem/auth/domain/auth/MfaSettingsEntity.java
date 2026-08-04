package com.banksystem.auth.domain.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_settings")
public class MfaSettingsEntity {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "secret_encrypted", nullable = false, columnDefinition = "TEXT")
  private String secretEncrypted;

  @Column(name = "enabled_at", nullable = false)
  private Instant enabledAt = Instant.now();

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getSecretEncrypted() {
    return secretEncrypted;
  }

  public void setSecretEncrypted(String secretEncrypted) {
    this.secretEncrypted = secretEncrypted;
  }

  public Instant getEnabledAt() {
    return enabledAt;
  }

  public void setEnabledAt(Instant enabledAt) {
    this.enabledAt = enabledAt;
  }
}
