package com.banksystem.transaction.domain.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "b2b_payouts")
public class B2bPayoutEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "settlement_leg_id", nullable = false)
  private UUID settlementLegId;

  @Column(name = "payout_type", nullable = false, length = 30)
  private String payoutType = "NAPAS_247";

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "beneficiary_account_id")
  private UUID beneficiaryAccountId;

  @Column(name = "beneficiary_bank_bin", nullable = false, length = 20)
  private String beneficiaryBankBin;

  @Column(name = "beneficiary_account_number", nullable = false, length = 50)
  private String beneficiaryAccountNumber;

  @Column(name = "beneficiary_name", nullable = false, length = 255)
  private String beneficiaryName;

  @Column(name = "provider_reference", length = 100)
  private String providerReference;

  @Column(name = "client_request_id", nullable = false, unique = true, length = 100)
  private String clientRequestId;

  @Column(name = "clearing_journal_id")
  private UUID clearingJournalId;

  @Column(name = "claim_token")
  private UUID claimToken;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "claim_expires_at")
  private Instant claimExpiresAt;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private B2bPayoutStatus status = B2bPayoutStatus.READY;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static B2bPayoutEntity create(
      UUID id,
      String clientRequestId,
      UUID organizationId,
      UUID settlementLegId,
      String payoutType,
      BigDecimal amount,
      String currency,
      UUID beneficiaryAccountId,
      String beneficiaryBankBin,
      String beneficiaryAccountNumber,
      String beneficiaryName,
      Instant now) {
    B2bPayoutEntity entity = new B2bPayoutEntity();
    entity.id = id;
    entity.clientRequestId = clientRequestId;
    entity.organizationId = organizationId;
    entity.settlementLegId = settlementLegId;
    entity.payoutType = payoutType != null ? payoutType : "NAPAS_247";
    entity.amount = amount;
    entity.currency = currency != null ? currency.toUpperCase() : "VND";
    entity.beneficiaryAccountId = beneficiaryAccountId;
    entity.beneficiaryBankBin = beneficiaryBankBin;
    entity.beneficiaryAccountNumber = beneficiaryAccountNumber;
    entity.beneficiaryName = beneficiaryName;
    entity.status = B2bPayoutStatus.READY;
    entity.retryCount = 0;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getClientRequestId() { return clientRequestId; }
  public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
  public UUID getClearingJournalId() { return clearingJournalId; }
  public void setClearingJournalId(UUID clearingJournalId) { this.clearingJournalId = clearingJournalId; }
  public UUID getClaimToken() { return claimToken; }
  public void setClaimToken(UUID claimToken) { this.claimToken = claimToken; }
  public Instant getClaimedAt() { return claimedAt; }
  public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
  public Instant getClaimExpiresAt() { return claimExpiresAt; }
  public void setClaimExpiresAt(Instant claimExpiresAt) { this.claimExpiresAt = claimExpiresAt; }
  public Instant getNextRetryAt() { return nextRetryAt; }
  public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public UUID getSettlementLegId() { return settlementLegId; }
  public void setSettlementLegId(UUID settlementLegId) { this.settlementLegId = settlementLegId; }
  public String getPayoutType() { return payoutType; }
  public void setPayoutType(String payoutType) { this.payoutType = payoutType; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public UUID getBeneficiaryAccountId() { return beneficiaryAccountId; }
  public void setBeneficiaryAccountId(UUID beneficiaryAccountId) { this.beneficiaryAccountId = beneficiaryAccountId; }
  public String getBeneficiaryBankBin() { return beneficiaryBankBin; }
  public void setBeneficiaryBankBin(String beneficiaryBankBin) { this.beneficiaryBankBin = beneficiaryBankBin; }
  public String getBeneficiaryAccountNumber() { return beneficiaryAccountNumber; }
  public void setBeneficiaryAccountNumber(String beneficiaryAccountNumber) { this.beneficiaryAccountNumber = beneficiaryAccountNumber; }
  public String getBeneficiaryName() { return beneficiaryName; }
  public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
  public String getProviderReference() { return providerReference; }
  public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
  public B2bPayoutStatus getStatus() { return status; }
  public void setStatus(B2bPayoutStatus status) { this.status = status; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
