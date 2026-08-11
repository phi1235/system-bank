package com.banksystem.customer.domain.kyc;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycCaseRepository extends JpaRepository<KycCaseEntity, UUID> {
  Optional<KycCaseEntity> findByCustomerIdAndCurrentTrue(UUID customerId);
}
