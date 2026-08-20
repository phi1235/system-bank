package com.banksystem.transaction.domain.settlement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_legs")
public class SettlementLegEntity {

  @Id
  private UUID id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "settlement_id", nullable = false)
  private SettlementEntity settlement;

  @Column(name = "leg_key", nullable = false, length = 100)
  private String legKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "beneficiary_type", nullable = false, length = 30)
  private BeneficiaryType beneficiaryType;

  @Column(name = "beneficiary_id", length = 100)
  private String beneficiaryId;

  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "bank_bin", length = 20)
  private String bankBin;

  @Column(name = "account_number", length = 50)
  private String accountNumber;

  @Column(name = "beneficiary_name", length = 255)
  private String beneficiaryName;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Enumerated(EnumType.STRING)
  @Column(name = "leg_type", nullable = false, length = 30)
  private SettlementLegType legType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SettlementLegStatus status = SettlementLegStatus.PENDING;

  @Column(name = "payout_id")
  private UUID payoutId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static SettlementLegEntity create(
      SettlementEntity settlement,
      String legKey,
      BeneficiaryType beneficiaryType,
      String beneficiaryId,
      UUID accountId,
      String bankBin,
      String accountNumber,
      String beneficiaryName,
      BigDecimal amount,
      String currency,
      SettlementLegType legType,
      Instant now) {
    SettlementLegEntity entity = new SettlementLegEntity();
    entity.id = UUID.randomUUID();
    entity.settlement = settlement;
    entity.legKey = legKey;
    entity.beneficiaryType = beneficiaryType;
    entity.beneficiaryId = beneficiaryId;
    entity.accountId = accountId;
    entity.bankBin = bankBin;
    entity.accountNumber = accountNumber;
    entity.beneficiaryName = beneficiaryName;
    entity.amount = amount;
    entity.currency = currency != null ? currency.toUpperCase() : "VND";
    entity.legType = legType;
    entity.status = SettlementLegStatus.PENDING;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public SettlementEntity getSettlement() { return settlement; }
  public void setSettlement(SettlementEntity settlement) { this.settlement = settlement; }
  public UUID getSettlementId() { return settlement != null ? settlement.getId() : null; }
  public String getLegKey() { return legKey; }
  public void setLegKey(String legKey) { this.legKey = legKey; }
  public BeneficiaryType getBeneficiaryType() { return beneficiaryType; }
  public void setBeneficiaryType(BeneficiaryType beneficiaryType) { this.beneficiaryType = beneficiaryType; }
  public String getBeneficiaryId() { return beneficiaryId; }
  public void setBeneficiaryId(String beneficiaryId) { this.beneficiaryId = beneficiaryId; }
  public UUID getAccountId() { return accountId; }
  public void setAccountId(UUID accountId) { this.accountId = accountId; }
  public String getBankBin() { return bankBin; }
  public void setBankBin(String bankBin) { this.bankBin = bankBin; }
  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
  public String getBeneficiaryName() { return beneficiaryName; }
  public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public SettlementLegType getLegType() { return legType; }
  public void setLegType(SettlementLegType legType) { this.legType = legType; }
  public SettlementLegStatus getStatus() { return status; }
  public void setStatus(SettlementLegStatus status) { this.status = status; }
  public UUID getPayoutId() { return payoutId; }
  public void setPayoutId(UUID payoutId) { this.payoutId = payoutId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
