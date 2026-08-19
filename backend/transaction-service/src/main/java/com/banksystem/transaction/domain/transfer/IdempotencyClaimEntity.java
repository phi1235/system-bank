package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_claims")
@IdClass(IdempotencyClaimEntity.IdempotencyKeyId.class)
public class IdempotencyClaimEntity {

  public static class IdempotencyKeyId implements Serializable {
    private UUID userId;
    private String idempotencyKey;

    public IdempotencyKeyId() {}

    public IdempotencyKeyId(UUID userId, String idempotencyKey) {
      this.userId = userId;
      this.idempotencyKey = idempotencyKey;
    }

    public UUID getUserId() {
      return userId;
    }

    public void setUserId(UUID userId) {
      this.userId = userId;
    }

    public String getIdempotencyKey() {
      return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IdempotencyKeyId that = (IdempotencyKeyId) o;
      return Objects.equals(userId, that.userId) && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, idempotencyKey);
    }
  }

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "status", nullable = false, length = 32)
  private String status = "PENDING";

  @Column(name = "response_status_code")
  private Integer responseStatusCode;

  @Column(name = "response_payload", columnDefinition = "TEXT")
  private String responsePayload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public IdempotencyClaimEntity() {}

  public IdempotencyClaimEntity(
      UUID userId,
      String idempotencyKey,
      String requestHash,
      String status,
      Instant expiresAt) {
    this.userId = userId;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.status = status;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getResponseStatusCode() {
    return responseStatusCode;
  }

  public void setResponseStatusCode(Integer responseStatusCode) {
    this.responseStatusCode = responseStatusCode;
  }

  public String getResponsePayload() {
    return responsePayload;
  }

  public void setResponsePayload(String responsePayload) {
    this.responsePayload = responsePayload;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
