package com.banksystem.transaction.application.forensics;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseDetailResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseHistoryResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryRepository;
import com.banksystem.transaction.domain.forensics.ForensicCasePriority;
import com.banksystem.transaction.domain.forensics.ForensicCaseRepository;
import com.banksystem.transaction.domain.forensics.ForensicCaseStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicCaseQueryService {
  private static final UUID EMPTY_UUID = new UUID(0L, 0L);
  private final ForensicCaseRepository caseRepository;
  private final ForensicCaseHistoryRepository historyRepository;
  private final ForensicFindingService findingService;
  private final ForensicInvestigationQueryService investigationQueryService;
  private final ForensicBusinessNarrativeService narrativeService;
  private final ForensicCaseMapper mapper;
  private final ForensicsFeatureGate featureGate;

  public ForensicCaseQueryService(
      ForensicCaseRepository caseRepository,
      ForensicCaseHistoryRepository historyRepository,
      ForensicFindingService findingService,
      ForensicInvestigationQueryService investigationQueryService,
      ForensicBusinessNarrativeService narrativeService,
      ForensicCaseMapper mapper,
      ForensicsFeatureGate featureGate) {
    this.caseRepository = caseRepository;
    this.historyRepository = historyRepository;
    this.findingService = findingService;
    this.investigationQueryService = investigationQueryService;
    this.narrativeService = narrativeService;
    this.mapper = mapper;
    this.featureGate = featureGate;
  }

  @Transactional(readOnly = true)
  public PageResponse<ForensicCaseResponse> search(ForensicCaseSearchQuery query) {
    featureGate.requireEnabled();
    Page<ForensicCaseEntity> page = caseRepository.search(
        query.status() != null, query.status() == null ? ForensicCaseStatus.OPEN : query.status(),
        query.priority() != null,
        query.priority() == null ? ForensicCasePriority.LOW : query.priority(),
        query.assignedTo() != null, query.assignedTo() == null ? EMPTY_UUID : query.assignedTo(),
        query.transactionId() != null,
        query.transactionId() == null ? EMPTY_UUID : query.transactionId(),
        query.q() != null, query.q() == null ? "" : query.q(), query.from(), query.to(),
        PageRequest.of(query.page(), query.size()));
    return new PageResponse<>(page.getContent().stream().map(mapper::toResponse).toList(),
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional
  public ForensicCaseDetailResponse get(UUID id) {
    featureGate.requireEnabled();
    ForensicCaseEntity entity = require(id);
    if (entity.getTransactionId() != null && (entity.getNarrativeJson() == null || entity.getNarrativeJson().isBlank())) {
      try {
        InvestigationDetailResponse investigation = investigationQueryService.get(entity.getTransactionId());
        narrativeService.getOrGenerateNarrative(entity, investigation);
      } catch (Exception ignored) {
        // Fallback gracefully without interrupting case detail view
      }
    }
    return new ForensicCaseDetailResponse(
        mapper.toResponse(entity), findingService.listForCase(id, entity.getTransactionId()));
  }

  @Transactional(readOnly = true)
  public PageResponse<ForensicCaseHistoryResponse> history(UUID id, int page, int size) {
    featureGate.requireEnabled();
    require(id);
    int normalizedPage = Math.max(page, 0);
    int normalizedSize = Math.min(Math.max(size, 1), 100);
    Page<ForensicCaseHistoryEntity> result = historyRepository
        .findByCaseIdOrderByCreatedAtDesc(id, PageRequest.of(normalizedPage, normalizedSize));
    return new PageResponse<>(result.getContent().stream().map(mapper::toHistory).toList(),
        result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  private ForensicCaseEntity require(UUID id) {
    return caseRepository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_CASE_NOT_FOUND", "Forensic case not found"));
  }
}
