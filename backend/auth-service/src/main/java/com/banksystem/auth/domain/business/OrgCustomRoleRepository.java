package com.banksystem.auth.domain.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgCustomRoleRepository extends JpaRepository<OrgCustomRoleEntity, UUID> {

  List<OrgCustomRoleEntity> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  Optional<OrgCustomRoleEntity> findByOrganizationIdAndCode(UUID organizationId, String code);

  Optional<OrgCustomRoleEntity> findByOrganizationIdAndOwnerRoleTrue(UUID organizationId);

  boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

  @Query("""
      SELECT DISTINCT p.permissionCode
      FROM OrgCustomRolePermissionEntity p
      WHERE p.roleId = :roleId
      """)
  List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);
}
