package com.banksystem.auth.domain.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessOrganizationRepository extends JpaRepository<BusinessOrganizationEntity, UUID> {
  Optional<BusinessOrganizationEntity> findByCode(String code);
  boolean existsByCode(String code);
  boolean existsByTaxNumber(String taxNumber);
  Optional<BusinessOrganizationEntity> findByTaxNumber(String taxNumber);
  Page<BusinessOrganizationEntity> findByKycStatus(String kycStatus, Pageable pageable);
  List<BusinessOrganizationEntity> findByKycStatusIn(List<String> kycStatuses);
}

