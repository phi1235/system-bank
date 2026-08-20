package com.banksystem.auth.domain.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessMemberRepository extends JpaRepository<BusinessMemberEntity, UUID> {
  List<BusinessMemberEntity> findByOrganizationId(UUID organizationId);
  List<BusinessMemberEntity> findByUserId(UUID userId);
  Optional<BusinessMemberEntity> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
  boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);
  void deleteByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
