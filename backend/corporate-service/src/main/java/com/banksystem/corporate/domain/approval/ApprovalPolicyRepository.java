package com.banksystem.corporate.domain.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicyEntity, UUID> {
  Optional<ApprovalPolicyEntity> findByCorporateIdAndStatus(UUID corporateId, String status);
  Optional<ApprovalPolicyEntity> findByCorporateIdAndVersionNumber(UUID corporateId, int versionNumber);
  List<ApprovalPolicyEntity> findByCorporateIdOrderByVersionNumberDesc(UUID corporateId);

  @Query("SELECT MAX(p.versionNumber) FROM ApprovalPolicyEntity p WHERE p.corporateId = :corporateId")
  Integer findMaxVersionNumber(@Param("corporateId") UUID corporateId);
}
