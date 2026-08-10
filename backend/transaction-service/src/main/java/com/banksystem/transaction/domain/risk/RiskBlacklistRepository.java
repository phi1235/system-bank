package com.banksystem.transaction.domain.risk;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskBlacklistRepository extends JpaRepository<RiskBlacklistEntity, UUID> {
  @Query("""
      SELECT b FROM RiskBlacklistEntity b
      WHERE b.active = true
        AND b.subjectType = :subjectType
        AND LOWER(b.subjectValue) = LOWER(:subjectValue)
        AND (b.expiresAt IS NULL OR b.expiresAt > :now)
      """)
  Optional<RiskBlacklistEntity> findActive(
      @Param("subjectType") String subjectType,
      @Param("subjectValue") String subjectValue,
      @Param("now") Instant now);

  List<RiskBlacklistEntity> findAllByOrderByCreatedAtDesc();
}
