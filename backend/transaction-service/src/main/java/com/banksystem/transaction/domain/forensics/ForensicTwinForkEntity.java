package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_twin_forks")
public class ForensicTwinForkEntity {
  @Id private UUID id;
  @Column(name = "transaction_id", nullable = false) private UUID transactionId;
  @Column(name = "created_by", nullable = false) private UUID createdBy;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "snapshot_uri", nullable = false, length = 500) private String snapshotUri;
  @Column(name = "snapshot_sha256", nullable = false, length = 64) private String snapshotSha256;
  @Column(name = "schema_version", nullable = false) private int schemaVersion;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "deleted_at") private Instant deletedAt;

  public static ForensicTwinForkEntity ready(
      UUID id, UUID transactionId, UUID actor, String uri, String checksum,
      Instant now, Instant expiresAt) {
    ForensicTwinForkEntity entity = new ForensicTwinForkEntity();
    entity.id = id;
    entity.transactionId = transactionId;
    entity.createdBy = actor;
    entity.status = "READY";
    entity.snapshotUri = uri;
    entity.snapshotSha256 = checksum;
    entity.schemaVersion = 1;
    entity.createdAt = now;
    entity.expiresAt = expiresAt;
    return entity;
  }

  public void delete(Instant now) { status = "DELETED"; deletedAt = now; }
  public UUID getId() { return id; }
  public UUID getTransactionId() { return transactionId; }
  public UUID getCreatedBy() { return createdBy; }
  public String getStatus() { return status; }
  public String getSnapshotUri() { return snapshotUri; }
  public String getSnapshotSha256() { return snapshotSha256; }
  public int getSchemaVersion() { return schemaVersion; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getDeletedAt() { return deletedAt; }
}
