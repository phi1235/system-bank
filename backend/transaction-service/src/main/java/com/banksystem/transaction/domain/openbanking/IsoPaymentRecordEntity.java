package com.banksystem.transaction.domain.openbanking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iso_payment_records")
public class IsoPaymentRecordEntity {

  @Id
  private UUID id;

  @Column(name = "message_id", nullable = false, length = 128)
  private String messageId;

  @Column(name = "client_id", nullable = false, length = 64)
  private String clientId;

  @Column(name = "instruction_id", nullable = false, length = 128)
  private String instructionId;

  @Column(name = "end_to_end_id", nullable = false, length = 128)
  private String endToEndId;

  @Column(name = "transfer_order_id")
  private UUID transferOrderId;

  @Column(name = "debtor_account", nullable = false, length = 32)
  private String debtorAccount;

  @Column(name = "creditor_account", nullable = false, length = 32)
  private String creditorAccount;

  @Column(name = "creditor_bank_code", length = 32)
  private String creditorBankCode;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(nullable = false, length = 10)
  private String status; // ACCP, ACSP, ACSC, RJCT

  @Column(name = "status_reason_code", length = 32)
  private String statusReasonCode;

  @Column(name = "status_reason_desc", columnDefinition = "TEXT")
  private String statusReasonDesc;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static IsoPaymentRecordEntity create(
      UUID id,
      String messageId,
      String clientId,
      String instructionId,
      String endToEndId,
      String debtorAccount,
      String creditorAccount,
      String creditorBankCode,
      BigDecimal amount,
      String currency,
      String status,
      String statusReasonCode,
      String statusReasonDesc,
      Instant now) {
    IsoPaymentRecordEntity entity = new IsoPaymentRecordEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.messageId = messageId.trim();
    entity.clientId = clientId.trim();
    entity.instructionId = instructionId.trim();
    entity.endToEndId = endToEndId.trim();
    entity.debtorAccount = debtorAccount.trim();
    entity.creditorAccount = creditorAccount.trim();
    entity.creditorBankCode = creditorBankCode != null ? creditorBankCode.trim() : null;
    entity.amount = amount != null ? amount : BigDecimal.ZERO;
    entity.currency = currency != null ? currency : "VND";
    entity.status = status != null ? status : "ACCP";
    entity.statusReasonCode = statusReasonCode;
    entity.statusReasonDesc = statusReasonDesc;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getMessageId() { return messageId; }
  public void setMessageId(String messageId) { this.messageId = messageId; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getInstructionId() { return instructionId; }
  public void setInstructionId(String instructionId) { this.instructionId = instructionId; }
  public String getEndToEndId() { return endToEndId; }
  public void setEndToEndId(String endToEndId) { this.endToEndId = endToEndId; }
  public UUID getTransferOrderId() { return transferOrderId; }
  public void setTransferOrderId(UUID transferOrderId) { this.transferOrderId = transferOrderId; }
  public String getDebtorAccount() { return debtorAccount; }
  public void setDebtorAccount(String debtorAccount) { this.debtorAccount = debtorAccount; }
  public String getCreditorAccount() { return creditorAccount; }
  public void setCreditorAccount(String creditorAccount) { this.creditorAccount = creditorAccount; }
  public String getCreditorBankCode() { return creditorBankCode; }
  public void setCreditorBankCode(String creditorBankCode) { this.creditorBankCode = creditorBankCode; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getStatusReasonCode() { return statusReasonCode; }
  public void setStatusReasonCode(String statusReasonCode) { this.statusReasonCode = statusReasonCode; }
  public String getStatusReasonDesc() { return statusReasonDesc; }
  public void setStatusReasonDesc(String statusReasonDesc) { this.statusReasonDesc = statusReasonDesc; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
