package com.banksystem.transaction.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_api_credentials")
public class MerchantApiCredentialEntity {

  @Id
  private UUID id;

  @Column(name = "key_id", nullable = false, unique = true, length = 50)
  private String keyId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "secret_hash", nullable = false, length = 100)
  private String secretHash;

  @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
  private String encryptedSecret;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static MerchantApiCredentialEntity create(
      String keyId, UUID organizationId, String name, String secretHash, String encryptedSecret, Instant expiresAt, Instant now) {
    MerchantApiCredentialEntity entity = new MerchantApiCredentialEntity();
    entity.id = UUID.randomUUID();
    entity.keyId = keyId;
    entity.organizationId = organizationId;
    entity.name = name;
    entity.secretHash = secretHash;
    entity.encryptedSecret = encryptedSecret;
    entity.status = "ACTIVE";
    entity.expiresAt = expiresAt;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getKeyId() { return keyId; }
  public void setKeyId(String keyId) { this.keyId = keyId; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSecretHash() { return secretHash; }
  public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
  public String getEncryptedSecret() { return encryptedSecret; }
  public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
