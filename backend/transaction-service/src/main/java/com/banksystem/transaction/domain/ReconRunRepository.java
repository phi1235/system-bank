package com.banksystem.transaction.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconRunRepository extends JpaRepository<ReconRunEntity, UUID> {

  Page<ReconRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
