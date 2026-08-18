package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_verification_watermarks")
public class ForensicVerificationWatermarkEntity {
  @Id @Column(name = "job_name", length = 80) private String jobName;
  @Column(nullable = false) private Instant watermark;
  @Column(name = "lease_owner") private UUID leaseOwner;
  @Column(name = "lease_until") private Instant leaseUntil;
  @Column(name = "last_error", length = 500) private String lastError;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public boolean claim(UUID owner, Instant now, Instant until) {
    if (leaseUntil != null && leaseUntil.isAfter(now) && !owner.equals(leaseOwner)) return false;
    leaseOwner = owner;
    leaseUntil = until;
    lastError = null;
    updatedAt = now;
    return true;
  }

  public void complete(UUID owner, Instant nextWatermark, Instant now) {
    requireOwner(owner);
    if (nextWatermark.isAfter(watermark)) watermark = nextWatermark;
    leaseOwner = null;
    leaseUntil = null;
    updatedAt = now;
  }

  public void fail(UUID owner, String error, Instant now) {
    requireOwner(owner);
    leaseOwner = null;
    leaseUntil = null;
    lastError = error == null ? "Batch verification failed" : error.substring(0, Math.min(500, error.length()));
    updatedAt = now;
  }

  private void requireOwner(UUID owner) {
    if (!owner.equals(leaseOwner)) throw new IllegalStateException("Verification lease is no longer owned");
  }

  public Instant getWatermark() { return watermark; }
}
