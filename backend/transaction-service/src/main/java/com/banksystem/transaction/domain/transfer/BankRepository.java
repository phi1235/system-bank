package com.banksystem.transaction.domain.transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<BankEntity, UUID> {
  List<BankEntity> findByStatusOrderByShortNameAsc(String status);
  Optional<BankEntity> findByCode(String code);
}
