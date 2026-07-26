package com.banksystem.account.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermDepositRepository extends JpaRepository<TermDepositEntity, UUID> {

  List<TermDepositEntity> findByUserIdOrderByOpenedAtDesc(UUID userId);
}
