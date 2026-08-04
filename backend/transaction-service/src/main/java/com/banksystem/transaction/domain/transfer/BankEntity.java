package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "banks")
public class BankEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 16)
  private String bin;

  @Column(name = "short_name", nullable = false, length = 64)
  private String shortName;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "logo_url")
  private String logoUrl;

  @Column(name = "napas_supported", nullable = false)
  private boolean napasSupported = true;

  @Column(name = "is_internal", nullable = false)
  private boolean isInternal = false;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public String getBin() { return bin; }
  public void setBin(String bin) { this.bin = bin; }

  public String getShortName() { return shortName; }
  public void setShortName(String shortName) { this.shortName = shortName; }

  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getLogoUrl() { return logoUrl; }
  public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

  public boolean isNapasSupported() { return napasSupported; }
  public void setNapasSupported(boolean napasSupported) { this.napasSupported = napasSupported; }

  public boolean isInternal() { return isInternal; }
  public void setInternal(boolean internal) { isInternal = internal; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
