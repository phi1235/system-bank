package com.banksystem.account.domain.sweep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "auto_sweep_operations")
public class AutoSweepOperationEntity {
  @Id private UUID id;
  @Column(name = "profile_id", nullable = false) private UUID profileId;
  @Column(name = "position_id", nullable = false) private UUID positionId;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "source_account_id", nullable = false) private UUID sourceAccountId;
  @Column(name = "operation_type", nullable = false, length = 30) private String operationType;
  @Column(name = "trigger_type", nullable = false, length = 20) private String triggerType;
  @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
  @Column(name = "annual_rate_bps") private Integer annualRateBps;
  @Column(name = "business_date", nullable = false) private LocalDate businessDate;
  @Column(name = "command_id", nullable = false, length = 180) private String commandId;
  @Column(name = "payment_reference", length = 128) private String paymentReference;
  @Column(name = "journal_id", nullable = false) private UUID journalId;
  @Column(name = "statement_entry_id") private UUID statementEntryId;
  @Column(name = "casa_balance_before", nullable = false, precision = 19, scale = 2)
  private BigDecimal casaBalanceBefore;
  @Column(name = "casa_balance_after", nullable = false, precision = 19, scale = 2)
  private BigDecimal casaBalanceAfter;
  @Column(name = "position_balance_before", nullable = false, precision = 19, scale = 2)
  private BigDecimal positionBalanceBefore;
  @Column(name = "position_balance_after", nullable = false, precision = 19, scale = 2)
  private BigDecimal positionBalanceAfter;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getProfileId() { return profileId; }
  public void setProfileId(UUID id) { this.profileId = id; }
  public UUID getPositionId() { return positionId; }
  public void setPositionId(UUID id) { this.positionId = id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID id) { this.userId = id; }
  public UUID getSourceAccountId() { return sourceAccountId; }
  public void setSourceAccountId(UUID id) { this.sourceAccountId = id; }
  public String getOperationType() { return operationType; }
  public void setOperationType(String value) { this.operationType = value; }
  public String getTriggerType() { return triggerType; }
  public void setTriggerType(String value) { this.triggerType = value; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal value) { this.amount = value; }
  public Integer getAnnualRateBps() { return annualRateBps; }
  public void setAnnualRateBps(Integer value) { this.annualRateBps = value; }
  public LocalDate getBusinessDate() { return businessDate; }
  public void setBusinessDate(LocalDate value) { this.businessDate = value; }
  public String getCommandId() { return commandId; }
  public void setCommandId(String value) { this.commandId = value; }
  public String getPaymentReference() { return paymentReference; }
  public void setPaymentReference(String value) { this.paymentReference = value; }
  public UUID getJournalId() { return journalId; }
  public void setJournalId(UUID id) { this.journalId = id; }
  public UUID getStatementEntryId() { return statementEntryId; }
  public void setStatementEntryId(UUID id) { this.statementEntryId = id; }
  public BigDecimal getCasaBalanceBefore() { return casaBalanceBefore; }
  public void setCasaBalanceBefore(BigDecimal value) { this.casaBalanceBefore = value; }
  public BigDecimal getCasaBalanceAfter() { return casaBalanceAfter; }
  public void setCasaBalanceAfter(BigDecimal value) { this.casaBalanceAfter = value; }
  public BigDecimal getPositionBalanceBefore() { return positionBalanceBefore; }
  public void setPositionBalanceBefore(BigDecimal value) { this.positionBalanceBefore = value; }
  public BigDecimal getPositionBalanceAfter() { return positionBalanceAfter; }
  public void setPositionBalanceAfter(BigDecimal value) { this.positionBalanceAfter = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { this.createdAt = value; }
}

