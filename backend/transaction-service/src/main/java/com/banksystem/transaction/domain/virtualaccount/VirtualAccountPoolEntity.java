package com.banksystem.transaction.domain.virtualaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "virtual_account_pools")
public class VirtualAccountPoolEntity {

  @Id
  private UUID id;

  @Column(nullable = false, length = 50)
  private String provider;

  @Column(name = "bank_bin", nullable = false, length = 20)
  private String bankBin;

  @Column(nullable = false, length = 20)
  private String prefix;

  @Column(name = "start_seq", nullable = false)
  private long startSeq;

  @Column(name = "end_seq", nullable = false)
  private long endSeq;

  @Column(name = "current_seq", nullable = false)
  private long currentSeq;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getBankBin() { return bankBin; }
  public void setBankBin(String bankBin) { this.bankBin = bankBin; }
  public String getPrefix() { return prefix; }
  public void setPrefix(String prefix) { this.prefix = prefix; }
  public long getStartSeq() { return startSeq; }
  public void setStartSeq(long startSeq) { this.startSeq = startSeq; }
  public long getEndSeq() { return endSeq; }
  public void setEndSeq(long endSeq) { this.endSeq = endSeq; }
  public long getCurrentSeq() { return currentSeq; }
  public void setCurrentSeq(long currentSeq) { this.currentSeq = currentSeq; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
