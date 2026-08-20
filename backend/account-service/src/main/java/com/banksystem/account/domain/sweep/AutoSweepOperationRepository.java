package com.banksystem.account.domain.sweep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoSweepOperationRepository extends JpaRepository<AutoSweepOperationEntity, UUID> {
  Optional<AutoSweepOperationEntity> findByCommandId(String commandId);
  List<AutoSweepOperationEntity> findByProfileIdOrderByCreatedAtDesc(UUID profileId, Pageable page);
}

