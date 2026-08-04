package com.banksystem.transaction.domain.transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<BeneficiaryEntity, UUID> {

  List<BeneficiaryEntity> findByUserIdAndActiveTrueOrderByNicknameAsc(UUID userId);

  Optional<BeneficiaryEntity> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndAccountNumber(UUID userId, String accountNumber);
}
