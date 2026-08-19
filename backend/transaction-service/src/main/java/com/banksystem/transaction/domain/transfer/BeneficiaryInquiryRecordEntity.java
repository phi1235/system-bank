package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiary_inquiry_records")
public class BeneficiaryInquiryRecordEntity {

  @Id
  private UUID id;

  @Column(name = "inquiry_id", nullable = false, unique = true, length = 64)
  private String inquiryId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "bank_bin", nullable = false, length = 16)
  private String bankBin;

  @Column(name = "account_number_encrypted", nullable = false, length = 512)
  private String accountNumberEncrypted;

  @Column(name = "account_number_hmac", nullable = false, length = 128)
  private String accountNumberHmac;

  @Column(name = "provider_account_name", nullable = false)
  private String providerAccountName;

  @Column(name = "account_type", nullable = false, length = 20)
  private String accountType = "INTERBANK";

  @Column(nullable = false, length = 20)
  private String status = "VERIFIED";

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(name = "key_version", nullable = false)
  private int keyVersion = 1;

  @Column(name = "verified_at", nullable = false)
  private Instant verifiedAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public BeneficiaryInquiryRecordEntity() {}

  public BeneficiaryInquiryRecordEntity(
      UUID id,
      String inquiryId,
      UUID userId,
      String bankBin,
      String accountNumberEncrypted,
      String accountNumberHmac,
      String providerAccountName,
      String accountType,
      String status,
      String provider,
      int keyVersion,
      Instant verifiedAt,
      Instant expiresAt) {
    this.id = id != null ? id : UUID.randomUUID();
    this.inquiryId = inquiryId;
    this.userId = userId;
    this.bankBin = bankBin;
    this.accountNumberEncrypted = accountNumberEncrypted;
    this.accountNumberHmac = accountNumberHmac;
    this.providerAccountName = providerAccountName;
    this.accountType = accountType != null ? accountType : "INTERBANK";
    this.status = status != null ? status : "VERIFIED";
    this.provider = provider;
    this.keyVersion = keyVersion;
    this.verifiedAt = verifiedAt != null ? verifiedAt : Instant.now();
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getInquiryId() { return inquiryId; }
  public void setInquiryId(String inquiryId) { this.inquiryId = inquiryId; }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public String getBankBin() { return bankBin; }
  public void setBankBin(String bankBin) { this.bankBin = bankBin; }

  public String getAccountNumberEncrypted() { return accountNumberEncrypted; }
  public void setAccountNumberEncrypted(String accountNumberEncrypted) { this.accountNumberEncrypted = accountNumberEncrypted; }

  public String getAccountNumberHmac() { return accountNumberHmac; }
  public void setAccountNumberHmac(String accountNumberHmac) { this.accountNumberHmac = accountNumberHmac; }

  public String getProviderAccountName() { return providerAccountName; }
  public void setProviderAccountName(String providerAccountName) { this.providerAccountName = providerAccountName; }

  public String getAccountType() { return accountType; }
  public void setAccountType(String accountType) { this.accountType = accountType; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }

  public int getKeyVersion() { return keyVersion; }
  public void setKeyVersion(int keyVersion) { this.keyVersion = keyVersion; }

  public Instant getVerifiedAt() { return verifiedAt; }
  public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getConsumedAt() { return consumedAt; }
  public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
