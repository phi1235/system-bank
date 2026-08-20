package com.banksystem.transaction.domain.merchant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccountEntity, UUID> {
  Optional<MerchantAccountEntity> findByOrganizationId(UUID organizationId);
  boolean existsByOrganizationId(UUID organizationId);
}
