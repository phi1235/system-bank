package com.banksystem.transaction.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_webhook_deliveries")
public class MerchantWebhookDeliveryEntity {

  @Id
  private UUID id;

  @Column(name = "endpoint_id", nullable = false)
  private UUID endpointId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private MerchantWebhookDeliveryStatus status = MerchantWebhookDeliveryStatus.PENDING;

  @Column(name = "response_status_code")
  private Integer responseStatusCode;

  @Column(name = "response_body", length = 1000)
  private String responseBody;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Column(name = "claim_token")
  private UUID claimToken;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "claim_expires_at")
  private Instant claimExpiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static MerchantWebhookDeliveryEntity create(
      UUID endpointId,
      UUID organizationId,
      UUID eventId,
      String eventType,
      String payload,
      Instant now) {
    MerchantWebhookDeliveryEntity entity = new MerchantWebhookDeliveryEntity();
    entity.id = UUID.randomUUID();
    entity.endpointId = endpointId;
    entity.organizationId = organizationId;
    entity.eventId = eventId;
    entity.eventType = eventType;
    entity.payload = payload;
    entity.status = MerchantWebhookDeliveryStatus.PENDING;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getEndpointId() { return endpointId; }
  public void setEndpointId(UUID endpointId) { this.endpointId = endpointId; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public UUID getEventId() { return eventId; }
  public void setEventId(UUID eventId) { this.eventId = eventId; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public MerchantWebhookDeliveryStatus getStatus() { return status; }
  public void setStatus(MerchantWebhookDeliveryStatus status) { this.status = status; }
  public Integer getResponseStatusCode() { return responseStatusCode; }
  public void setResponseStatusCode(Integer responseStatusCode) { this.responseStatusCode = responseStatusCode; }
  public String getResponseBody() { return responseBody; }
  public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public Instant getNextRetryAt() { return nextRetryAt; }
  public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
  public UUID getClaimToken() { return claimToken; }
  public void setClaimToken(UUID claimToken) { this.claimToken = claimToken; }
  public Instant getClaimedAt() { return claimedAt; }
  public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
  public Instant getClaimExpiresAt() { return claimExpiresAt; }
  public void setClaimExpiresAt(Instant claimExpiresAt) { this.claimExpiresAt = claimExpiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
