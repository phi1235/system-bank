package com.banksystem.auth.domain.b2b;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "b2b_account_consents")
public class B2bAccountConsentEntity {

  @Id
  private UUID id;

  @Column(name = "client_id", nullable = false, length = 64)
  private String clientId;

  @Column(name = "account_number", nullable = false, length = 32)
  private String accountNumber;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(nullable = false, length = 256)
  private String permissions;

  @Column(nullable = false, length = 20)
  private String status = "AUTHORISED";

  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public static B2bAccountConsentEntity create(
      UUID id,
      String clientId,
      String accountNumber,
      UUID customerId,
      String permissions,
      Instant validUntil,
      Instant now) {
    B2bAccountConsentEntity entity = new B2bAccountConsentEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.clientId = clientId.trim();
    entity.accountNumber = accountNumber.trim();
    entity.customerId = customerId;
    entity.permissions = permissions != null ? permissions.trim() : "ReadAccountsDetail,ReadBalances,ReadStatements,CreateSinglePayment,CreateBulkPayment";
    entity.status = "AUTHORISED";
    entity.validUntil = validUntil != null ? validUntil : now.plusSeconds(86400 * 365); // 1 year default
    entity.createdAt = now;
    return entity;
  }

  public List<String> permissionList() {
    if (permissions == null || permissions.isBlank()) {
      return List.of();
    }
    return Arrays.stream(permissions.split("[\\s,]+"))
        .filter(s -> !s.isBlank())
        .toList();
  }

  public boolean isValid(Instant now) {
    return "AUTHORISED".equalsIgnoreCase(status) && (validUntil == null || validUntil.isAfter(now));
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID customerId) { this.customerId = customerId; }
  public String getPermissions() { return permissions; }
  public void setPermissions(String permissions) { this.permissions = permissions; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getValidUntil() { return validUntil; }
  public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
