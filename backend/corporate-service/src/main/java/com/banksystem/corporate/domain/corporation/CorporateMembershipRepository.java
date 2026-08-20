package com.banksystem.corporate.domain.corporation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorporateMembershipRepository extends JpaRepository<CorporateMembershipEntity, UUID> {
  Optional<CorporateMembershipEntity> findByCorporateIdAndUserId(UUID corporateId, UUID userId);
  List<CorporateMembershipEntity> findByUserIdAndStatus(UUID userId, String status);
  List<CorporateMembershipEntity> findByCorporateId(UUID corporateId);
  boolean existsByCorporateIdAndUserIdAndStatus(UUID corporateId, UUID userId, String status);

  @Query("""
      SELECT m FROM CorporateMembershipEntity m
      JOIN FETCH m.roles r
      WHERE m.corporateId = :corporateId 
        AND m.userId = :userId 
        AND m.status = 'ACTIVE'
        AND (m.expiresAt IS NULL OR m.expiresAt > CURRENT_TIMESTAMP)
      """)
  Optional<CorporateMembershipEntity> findActiveWithRoles(
      @Param("corporateId") UUID corporateId,
      @Param("userId") UUID userId);

  @Query("""
      SELECT m FROM CorporateMembershipEntity m
      JOIN m.roles r
      WHERE m.corporateId = :corporateId 
        AND r.roleName = :roleName 
        AND m.status = 'ACTIVE'
        AND (m.expiresAt IS NULL OR m.expiresAt > CURRENT_TIMESTAMP)
      """)
  List<CorporateMembershipEntity> findByCorporateIdAndRole(
      @Param("corporateId") UUID corporateId,
      @Param("roleName") String roleName);
}
