package com.banksystem.corporate.domain.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTaskEntity, UUID> {
  List<ApprovalTaskEntity> findByBatchIdOrderByStepOrderAsc(UUID batchId);
  List<ApprovalTaskEntity> findByInstanceIdOrderByStepOrderAsc(UUID instanceId);
  Optional<ApprovalTaskEntity> findByInstanceIdAndStepOrder(UUID instanceId, int stepOrder);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM ApprovalTaskEntity t JOIN FETCH t.batch WHERE t.id = :id")
  Optional<ApprovalTaskEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      SELECT t FROM ApprovalTaskEntity t
      JOIN t.batch b
      WHERE t.requiredRole IN :roles AND t.status = 'ACTIVE' AND b.corporateId = :corporateId
      ORDER BY t.createdAt DESC
      """)
  List<ApprovalTaskEntity> findActiveTasksForRoles(
      @Param("corporateId") UUID corporateId,
      @Param("roles") List<String> roles);

  @Query("""
      SELECT t FROM ApprovalTaskEntity t
      JOIN t.batch b
      JOIN CorporateMembershipEntity m ON m.corporateId = b.corporateId
      JOIN m.roles r ON r.roleName = t.requiredRole
      WHERE m.userId = :userId AND m.status = 'ACTIVE'
        AND (m.expiresAt IS NULL OR m.expiresAt > CURRENT_TIMESTAMP)
        AND t.status = 'ACTIVE'
      ORDER BY t.createdAt DESC
      """)
  List<ApprovalTaskEntity> findInboxForUser(@Param("userId") UUID userId);
}
