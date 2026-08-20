package com.banksystem.auth.domain.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessOrganizationRepository extends JpaRepository<BusinessOrganizationEntity, UUID> {
  Optional<BusinessOrganizationEntity> findByCode(String code);
  boolean existsByCode(String code);
}
