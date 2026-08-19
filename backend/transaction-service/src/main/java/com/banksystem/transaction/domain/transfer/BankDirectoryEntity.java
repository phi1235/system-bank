package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_directory")
public class BankDirectoryEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 16)
  private String bin;

  @Column(nullable = false, length = 32)
  private String code;

  @Column(name = "short_name", nullable = false, length = 64)
  private String shortName;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "logo_url")
  private String logoUrl;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "lookup_supported", nullable = false)
  private boolean lookupSupported = false;

  @Column(name = "qr_transfer_supported", nullable = false)
  private boolean qrTransferSupported = false;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "last_sync_status", length = 20)
  private String lastSyncStatus = "SUCCESS";

  @Column(name = "last_sync_error")
  private String lastSyncError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public BankDirectoryEntity() {}

  public BankDirectoryEntity(
      UUID id,
      String bin,
      String code,
      String shortName,
      String fullName,
      String logoUrl,
      boolean active) {
    this.id = id != null ? id : UUID.randomUUID();
    this.bin = bin;
    this.code = code;
    this.shortName = shortName;
    this.fullName = fullName;
    this.logoUrl = logoUrl;
    this.active = active;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getBin() { return bin; }
  public void setBin(String bin) { this.bin = bin; }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public String getShortName() { return shortName; }
  public void setShortName(String shortName) { this.shortName = shortName; }

  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getLogoUrl() { return logoUrl; }
  public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public boolean isLookupSupported() { return lookupSupported; }
  public void setLookupSupported(boolean lookupSupported) { this.lookupSupported = lookupSupported; }

  public boolean isQrTransferSupported() { return qrTransferSupported; }
  public void setQrTransferSupported(boolean qrTransferSupported) { this.qrTransferSupported = qrTransferSupported; }

  public Instant getLastSyncedAt() { return lastSyncedAt; }
  public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

  public String getLastSyncStatus() { return lastSyncStatus; }
  public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }

  public String getLastSyncError() { return lastSyncError; }
  public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
