package com.banksystem.transaction.domain.transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankDirectoryRepository extends JpaRepository<BankDirectoryEntity, UUID> {

  Optional<BankDirectoryEntity> findByBin(String bin);

  Optional<BankDirectoryEntity> findByCodeIgnoreCase(String code);

  List<BankDirectoryEntity> findByActiveTrueOrderByShortNameAsc();
}
