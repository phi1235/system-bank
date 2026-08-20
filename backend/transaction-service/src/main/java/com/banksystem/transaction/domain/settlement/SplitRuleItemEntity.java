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
@Table(name = "split_rule_items")
public class SplitRuleItemEntity {

  @Id
  private UUID id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "split_rule_id", nullable = false)
  private SplitRuleEntity splitRule;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "split_type", nullable = false, length = 30)
  private SplitType splitType;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal value;

  @Column(nullable = false)
  private int priority = 1;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public static SplitRuleItemEntity create(
      SplitRuleEntity splitRule,
      BeneficiaryType beneficiaryType,
      String beneficiaryId,
      UUID accountId,
      String bankBin,
      String accountNumber,
      String beneficiaryName,
      SplitType splitType,
      BigDecimal value,
      int priority,
      Instant now) {
    SplitRuleItemEntity entity = new SplitRuleItemEntity();
    entity.id = UUID.randomUUID();
    entity.splitRule = splitRule;
    entity.beneficiaryType = beneficiaryType;
    entity.beneficiaryId = beneficiaryId;
    entity.accountId = accountId;
    entity.bankBin = bankBin;
    entity.accountNumber = accountNumber;
    entity.beneficiaryName = beneficiaryName;
    entity.splitType = splitType;
    entity.value = value;
    entity.priority = priority;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public SplitRuleEntity getSplitRule() { return splitRule; }
  public void setSplitRule(SplitRuleEntity splitRule) { this.splitRule = splitRule; }
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
  public SplitType getSplitType() { return splitType; }
  public void setSplitType(SplitType splitType) { this.splitType = splitType; }
  public BigDecimal getValue() { return value; }
  public void setValue(BigDecimal value) { this.value = value; }
  public int getPriority() { return priority; }
  public void setPriority(int priority) { this.priority = priority; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
