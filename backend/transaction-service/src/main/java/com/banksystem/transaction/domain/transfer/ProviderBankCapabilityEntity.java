package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "provider_bank_capabilities",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "bank_bin"})
)
public class ProviderBankCapabilityEntity {

  @Id
  private UUID id;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(name = "bank_bin", nullable = false, length = 16)
  private String bankBin;

  @Column(name = "inquiry_supported", nullable = false)
  private boolean inquirySupported = false;

  @Column(name = "payout_supported", nullable = false)
  private boolean payoutSupported = false;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(nullable = false, length = 64)
  private String source = "PARTNER_CONFIG";

  @Column(name = "last_checked_at", nullable = false)
  private Instant lastCheckedAt = Instant.now();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public ProviderBankCapabilityEntity() {}

  public ProviderBankCapabilityEntity(
      UUID id,
      String provider,
      String bankBin,
      boolean inquirySupported,
      boolean payoutSupported,
      String status,
      String source) {
    this.id = id != null ? id : UUID.randomUUID();
    this.provider = provider;
    this.bankBin = bankBin;
    this.inquirySupported = inquirySupported;
    this.payoutSupported = payoutSupported;
    this.status = status != null ? status : "ACTIVE";
    this.source = source != null ? source : "PARTNER_CONFIG";
    this.lastCheckedAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }

  public String getBankBin() { return bankBin; }
  public void setBankBin(String bankBin) { this.bankBin = bankBin; }

  public boolean isInquirySupported() { return inquirySupported; }
  public void setInquirySupported(boolean inquirySupported) { this.inquirySupported = inquirySupported; }

  public boolean isPayoutSupported() { return payoutSupported; }
  public void setPayoutSupported(boolean payoutSupported) { this.payoutSupported = payoutSupported; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }

  public Instant getLastCheckedAt() { return lastCheckedAt; }
  public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
