package com.banksystem.transaction.domain.collection;

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
@Table(name = "inbound_payment_events")
public class InboundPaymentEventEntity {

  @Id
  private UUID id;

  @Column(nullable = false, length = 50)
  private String provider;

  @Column(name = "provider_transaction_id", nullable = false, length = 100)
  private String providerTransactionId;

  @Column(name = "virtual_account_number", nullable = false, length = 50)
  private String virtualAccountNumber;

  @Column(name = "bank_bin", nullable = false, length = 20)
  private String bankBin;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "sender_account", length = 50)
  private String senderAccount;

  @Column(name = "sender_bank_bin", length = 20)
  private String senderBankBin;

  @Column(name = "sender_name", length = 255)
  private String senderName;

  @Column(name = "reference_content", length = 500)
  private String referenceContent;

  @Column(name = "raw_payload_hash", nullable = false, length = 64)
  private String rawPayloadHash;

  @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
  private String rawPayload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private InboundPaymentStatus status = InboundPaymentStatus.RECEIVED;

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

  @Column(name = "ledger_journal_id")
  private UUID ledgerJournalId;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public static InboundPaymentEventEntity create(
      String provider,
      String providerTransactionId,
      String virtualAccountNumber,
      String bankBin,
      BigDecimal amount,
      String currency,
      String senderAccount,
      String senderBankBin,
      String senderName,
      String referenceContent,
      String rawPayloadHash,
      String rawPayload,
      Instant now) {
    InboundPaymentEventEntity entity = new InboundPaymentEventEntity();
    entity.id = UUID.randomUUID();
    entity.provider = provider.toUpperCase();
    entity.providerTransactionId = providerTransactionId;
    entity.virtualAccountNumber = virtualAccountNumber;
    entity.bankBin = bankBin;
    entity.amount = amount;
    entity.currency = currency != null ? currency.toUpperCase() : "VND";
    entity.senderAccount = senderAccount;
    entity.senderBankBin = senderBankBin;
    entity.senderName = senderName;
    entity.referenceContent = referenceContent;
    entity.rawPayloadHash = rawPayloadHash;
    entity.rawPayload = rawPayload;
    entity.status = InboundPaymentStatus.RECEIVED;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getProviderTransactionId() { return providerTransactionId; }
  public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }
  public String getVirtualAccountNumber() { return virtualAccountNumber; }
  public void setVirtualAccountNumber(String virtualAccountNumber) { this.virtualAccountNumber = virtualAccountNumber; }
  public String getBankBin() { return bankBin; }
  public void setBankBin(String bankBin) { this.bankBin = bankBin; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getSenderAccount() { return senderAccount; }
  public void setSenderAccount(String senderAccount) { this.senderAccount = senderAccount; }
  public String getSenderBankBin() { return senderBankBin; }
  public void setSenderBankBin(String senderBankBin) { this.senderBankBin = senderBankBin; }
  public String getSenderName() { return senderName; }
  public void setSenderName(String senderName) { this.senderName = senderName; }
  public String getReferenceContent() { return referenceContent; }
  public void setReferenceContent(String referenceContent) { this.referenceContent = referenceContent; }
  public String getRawPayloadHash() { return rawPayloadHash; }
  public void setRawPayloadHash(String rawPayloadHash) { this.rawPayloadHash = rawPayloadHash; }
  public String getRawPayload() { return rawPayload; }
  public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
  public InboundPaymentStatus getStatus() { return status; }
  public void setStatus(InboundPaymentStatus status) { this.status = status; }
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
  public UUID getLedgerJournalId() { return ledgerJournalId; }
  public void setLedgerJournalId(UUID ledgerJournalId) { this.ledgerJournalId = ledgerJournalId; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
