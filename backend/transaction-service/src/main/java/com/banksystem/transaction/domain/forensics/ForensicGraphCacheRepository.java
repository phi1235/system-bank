package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ForensicGraphCacheRepository extends JpaRepository<ForensicGraphCacheEntity, UUID> {
  @Modifying
  @Query(value = """
      INSERT INTO forensic_graph_cache
        (transaction_id, graph_version, completeness, source_watermark, graph_json, expires_at, updated_at)
      VALUES (:transactionId, :version, :completeness, :watermark,
              CAST(:graphJson AS jsonb), :expiresAt, :now)
      ON CONFLICT (transaction_id) DO UPDATE SET
        graph_version = EXCLUDED.graph_version,
        completeness = EXCLUDED.completeness,
        source_watermark = EXCLUDED.source_watermark,
        graph_json = EXCLUDED.graph_json,
        expires_at = EXCLUDED.expires_at,
        updated_at = EXCLUDED.updated_at
      """, nativeQuery = true)
  int upsert(
      @Param("transactionId") UUID transactionId,
      @Param("version") long version,
      @Param("completeness") String completeness,
      @Param("watermark") Instant watermark,
      @Param("graphJson") String graphJson,
      @Param("expiresAt") Instant expiresAt,
      @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("DELETE FROM ForensicGraphCacheEntity c WHERE c.expiresAt <= :now")
  int deleteExpired(@Param("now") Instant now);
}
