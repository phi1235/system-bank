package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "forensic_graph_cache")
public class ForensicGraphCacheEntity {
  @Id @Column(name = "transaction_id") private UUID transactionId;
  @Column(name = "graph_version", nullable = false) private long graphVersion;
  @Column(nullable = false, length = 30) private String completeness;
  @Column(name = "source_watermark", nullable = false) private Instant sourceWatermark;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "graph_json", nullable = false, columnDefinition = "jsonb") private String graphJson;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public boolean isFresh(long version, Instant watermark, Instant now) {
    return graphVersion == version && sourceWatermark.equals(watermark) && expiresAt.isAfter(now);
  }

  public String getGraphJson() { return graphJson; }
}
