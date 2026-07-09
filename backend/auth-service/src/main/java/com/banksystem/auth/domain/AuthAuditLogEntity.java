package com.banksystem.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_log")
public class AuthAuditLogEntity {

  @Id
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(length = 64)
  private String ip;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public static AuthAuditLogEntity of(UUID userId, String action, String ip, String detail) {
    AuthAuditLogEntity e = new AuthAuditLogEntity();
    e.id = UUID.randomUUID();
    e.userId = userId;
    e.action = action;
    e.ip = ip;
    e.detail = detail;
    e.createdAt = Instant.now();
    return e;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getAction() {
    return action;
  }

  public String getIp() {
    return ip;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
