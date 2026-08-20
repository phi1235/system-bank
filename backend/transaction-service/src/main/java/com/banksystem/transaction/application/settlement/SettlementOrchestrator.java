package com.banksystem.transaction.application.settlement;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementLegResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementResponse;
import com.banksystem.transaction.domain.settlement.SettlementEntity;
import com.banksystem.transaction.domain.settlement.SettlementRepository;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class SettlementOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(SettlementOrchestrator.class);

  private final SettlementFacade settlementFacade;
  private final SettlementRepository settlementRepository;

  public SettlementOrchestrator(
      SettlementFacade settlementFacade,
      SettlementRepository settlementRepository) {
    this.settlementFacade = settlementFacade;
    this.settlementRepository = settlementRepository;
  }

  public SettlementResponse completeOrder(UUID organizationId, UUID orderId, UUID actorId, String actorRole) {
    log.info("[SETTLEMENT-ORCHESTRATOR] completeOrder requested by actor [{}] role [{}] for org [{}] order [{}]",
        actorId, actorRole, organizationId, orderId);
    return settlementFacade.completeOrder(organizationId, orderId);
  }

  public SettlementResponse retrySettlement(UUID organizationId, UUID settlementId, UUID actorId, String actorRole) {
    log.info("[SETTLEMENT-ORCHESTRATOR] retrySettlement requested by actor [{}] role [{}] for settlement [{}]",
        actorId, actorRole, settlementId);
    SettlementEntity settlement = settlementRepository.findById(settlementId)
        .orElseThrow(() -> new BusinessException("SETTLEMENT_NOT_FOUND", "Settlement not found: " + settlementId));

    if (organizationId != null && !settlement.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to settlement");
    }

    return settlementFacade.completeOrder(organizationId, settlement.getCollectionOrderId());
  }

  public SettlementResponse getById(UUID organizationId, UUID settlementId) {
    return settlementFacade.getSettlement(organizationId, settlementId);
  }

  public Page<SettlementResponse> search(SettlementSearchQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Page<SettlementEntity> page = settlementRepository.search(query.organizationId(), query.status(), pageable);
    return page.map(this::toResponse);
  }

  public Page<SettlementResponse> search(UUID organizationId, SettlementStatus status, Pageable pageable) {
    Page<SettlementEntity> page = settlementRepository.search(organizationId, status, pageable);
    return page.map(this::toResponse);
  }

  private SettlementResponse toResponse(SettlementEntity settlement) {
    List<SettlementLegResponse> legResponses = settlement.getLegs().stream()
        .map(l -> new SettlementLegResponse(
            l.getId(), l.getBeneficiaryType(), l.getBeneficiaryId(), l.getAccountId(),
            l.getBankBin(), l.getAccountNumber(), l.getBeneficiaryName(),
            l.getAmount(), l.getCurrency(), l.getLegType(), l.getStatus(), l.getPayoutId()
        ))
        .toList();

    return new SettlementResponse(
        settlement.getId(),
        settlement.getOrganizationId(),
        settlement.getCollectionOrderId(),
        settlement.getGrossAmount(),
        settlement.getPlatformCommission(),
        settlement.getSellerNetAmount(),
        settlement.getCurrency(),
        settlement.getStatus(),
        settlement.getLedgerJournalId(),
        settlement.getFailureReason(),
        legResponses,
        settlement.getCreatedAt(),
        settlement.getUpdatedAt()
    );
  }
}
