package com.banksystem.corporate.domain.corporation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorporationRepository extends JpaRepository<CorporationEntity, UUID> {
  Optional<CorporationEntity> findByTaxId(String taxId);
  boolean existsByTaxId(String taxId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM CorporationEntity c WHERE c.id = :id")
  Optional<CorporationEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      SELECT c FROM CorporationEntity c
      JOIN c.memberships m
      WHERE m.userId = :userId AND m.status = 'ACTIVE' AND c.status = 'ACTIVE'
      """)
  List<CorporationEntity> findActiveCorporationsForUser(@Param("userId") UUID userId);
}
