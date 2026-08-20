package com.banksystem.transaction.domain.merchant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantApiCredentialRepository extends JpaRepository<MerchantApiCredentialEntity, UUID> {
  Optional<MerchantApiCredentialEntity> findByKeyId(String keyId);
  List<MerchantApiCredentialEntity> findByOrganizationId(UUID organizationId);
  boolean existsByKeyId(String keyId);
}
