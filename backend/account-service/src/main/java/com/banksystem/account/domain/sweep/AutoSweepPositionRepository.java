package com.banksystem.account.domain.sweep;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoSweepPositionRepository extends JpaRepository<AutoSweepPositionEntity, UUID> {
  Optional<AutoSweepPositionEntity> findByProfileId(UUID profileId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM AutoSweepPositionEntity p WHERE p.profileId = :profileId")
  Optional<AutoSweepPositionEntity> findByProfileIdForUpdate(@Param("profileId") UUID profileId);
}

