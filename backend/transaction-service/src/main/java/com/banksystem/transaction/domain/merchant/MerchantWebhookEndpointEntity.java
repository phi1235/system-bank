package com.banksystem.transaction.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_webhook_endpoints")
public class MerchantWebhookEndpointEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 500)
  private String url;

  @Column(name = "event_types", nullable = false, length = 255)
  private String eventTypes = "collection.order.paid.v1";

  @Column(name = "secret_hash", nullable = false, length = 100)
  private String secretHash;

  @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
  private String encryptedSecret;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static MerchantWebhookEndpointEntity create(
      UUID organizationId, String url, String eventTypes, String secretHash, String encryptedSecret, Instant now) {
    MerchantWebhookEndpointEntity entity = new MerchantWebhookEndpointEntity();
    entity.id = UUID.randomUUID();
    entity.organizationId = organizationId;
    entity.url = url;
    entity.eventTypes = eventTypes != null ? eventTypes : "collection.order.paid.v1";
    entity.secretHash = secretHash;
    entity.encryptedSecret = encryptedSecret;
    entity.status = "ACTIVE";
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }
  public String getEventTypes() { return eventTypes; }
  public void setEventTypes(String eventTypes) { this.eventTypes = eventTypes; }
  public String getSecretHash() { return secretHash; }
  public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
  public String getEncryptedSecret() { return encryptedSecret; }
  public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
