package com.banksystem.customer.domain.kyc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocumentEntity, UUID> {
  List<KycDocumentEntity> findByCaseIdOrderByUploadedAtAsc(UUID caseId);
  long countByCaseId(UUID caseId);
}
