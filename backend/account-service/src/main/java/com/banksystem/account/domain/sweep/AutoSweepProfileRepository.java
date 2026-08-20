package com.banksystem.account.domain.sweep;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface AutoSweepProfileRepository extends JpaRepository<AutoSweepProfileEntity, UUID> {
  Optional<AutoSweepProfileEntity> findBySourceAccountId(UUID sourceAccountId);
  List<AutoSweepProfileEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM AutoSweepProfileEntity p WHERE p.sourceAccountId = :sourceAccountId")
  Optional<AutoSweepProfileEntity> findBySourceAccountIdForUpdate(
      @Param("sourceAccountId") UUID sourceAccountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM AutoSweepProfileEntity p WHERE p.id = :id")
  Optional<AutoSweepProfileEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("SELECT p.id FROM AutoSweepProfileEntity p WHERE p.status = 'ENABLED' ORDER BY p.id")
  List<UUID> findEnabledIds(Pageable pageable);
}
