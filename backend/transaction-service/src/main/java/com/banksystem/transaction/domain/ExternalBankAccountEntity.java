package com.banksystem.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_bank_accounts")
public class ExternalBankAccountEntity {

  @Id
  private UUID id;

  @Column(name = "bank_code", nullable = false, length = 32)
  private String bankCode;

  @Column(name = "account_number", nullable = false, length = 32)
  private String accountNumber;

  @Column(name = "account_holder_name", nullable = false, length = 128)
  private String accountHolderName;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getBankCode() { return bankCode; }
  public void setBankCode(String bankCode) { this.bankCode = bankCode; }

  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

  public String getAccountHolderName() { return accountHolderName; }
  public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
