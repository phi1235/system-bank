package com.banksystem.transaction.application.settlement;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementFinalizedContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementLegPreparedContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementLegResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementPreparedContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementResponse;
import com.banksystem.transaction.domain.settlement.SettlementEntity;
import com.banksystem.transaction.domain.settlement.SettlementRepository;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.AtomicPostingView;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.SettlementLegCommand;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.SettlementPostingCommand;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettlementFacade {

  private static final Logger log = LoggerFactory.getLogger(SettlementFacade.class);

  private final SettlementTransactionService settlementTxService;
  private final SettlementRepository settlementRepository;
  private final AccountAtomicLedgerClient accountAtomicLedgerClient;

  public SettlementFacade(
      SettlementTransactionService settlementTxService,
      SettlementRepository settlementRepository,
      AccountAtomicLedgerClient accountAtomicLedgerClient) {
    this.settlementTxService = settlementTxService;
    this.settlementRepository = settlementRepository;
    this.accountAtomicLedgerClient = accountAtomicLedgerClient;
  }

  public SettlementResponse completeOrder(UUID businessId, UUID orderId) {
    log.info("[SETTLEMENT-FACADE] Starting settlement orchestration for business [{}] order [{}]", businessId, orderId);

    // Phase 1 (TX A): Prepare settlement & legs in DB
    SettlementPreparedContext prepared = settlementTxService.prepareSettlement(businessId, orderId);

    // Check if settlement was already posted/completed
    if (prepared.status() == SettlementStatus.PAYOUT_PENDING
        || prepared.status() == SettlementStatus.COMPLETED) {
      log.info("[SETTLEMENT-FACADE] Settlement [{}] already finalized with status {}", prepared.settlementId(), prepared.status());
      return getSettlement(businessId, prepared.settlementId());
    }

    if (prepared.status() == SettlementStatus.LEDGER_POSTED) {
      if (prepared.ledgerJournalId() == null) {
        throw new BusinessException(
            "SETTLEMENT_MANUAL_REVIEW_REQUIRED",
            "Legacy settlement is marked ledger-posted without a journal reference");
      }
      settlementTxService.finalizeSettlementLedger(
          prepared.settlementId(), prepared.ledgerJournalId());
      return getSettlement(businessId, prepared.settlementId());
    }

    // Phase 2: Call Remote Atomic Ledger Service outside DB Transaction
    List<SettlementLegCommand> ledgerLegs = new ArrayList<>();
    for (SettlementLegPreparedContext leg : prepared.legs()) {
      ledgerLegs.add(new SettlementLegCommand(
          leg.accountId(),
          leg.ledgerAccountCode(),
          leg.amount(),
          "Settlement leg " + leg.legKey()
      ));
    }

    String ledgerCommandId = "SETTLEMENT_ESCROW:" + prepared.settlementId();
    SettlementPostingCommand ledgerCommand = new SettlementPostingCommand(
        ledgerCommandId,
        "SETTLEMENT:" + prepared.settlementId(),
        prepared.settlementId(),
        prepared.merchantEscrowAccountId(),
        prepared.currency(),
        prepared.grossAmount().add(prepared.overpaidAmount()),
        ledgerLegs,
        "Settlement escrow posting for order " + prepared.collectionOrderId()
    );

    ApiResponse<AtomicPostingView> ledgerResp;
    try {
      ledgerResp = accountAtomicLedgerClient.recordSettlement(ledgerCommand);
    } catch (Exception ex) {
      log.error("[SETTLEMENT-FACADE] Remote ledger recording failed for settlement {}: {}", prepared.settlementId(), ex.getMessage());
      throw new BusinessException("LEDGER_POSTING_FAILED", "Failed to debit merchant escrow ledger: " + ex.getMessage());
    }

    if (ledgerResp == null || ledgerResp.data() == null) {
      throw new BusinessException("LEDGER_INDETERMINATE", "Indeterminate response from account-service for settlement " + prepared.settlementId());
    }

    AtomicPostingView posting = ledgerResp.data();
    BigDecimal expectedLedgerAmount = prepared.grossAmount().add(prepared.overpaidAmount());
    if (posting.journalId() == null
        || !ledgerCommandId.equals(posting.businessCommandId())
        || expectedLedgerAmount.compareTo(posting.amount()) != 0) {
      throw new BusinessException(
          "LEDGER_INDETERMINATE", "Account ledger response does not match settlement command");
    }
    UUID journalId = posting.journalId();
    log.info("[SETTLEMENT-FACADE] Settlement [{}] escrow ledger posted (journalId={})", prepared.settlementId(), journalId);

    // Phase 3 (TX B): Finalize ledger journal, mark internal legs, and persist ready payouts
    SettlementFinalizedContext finalized = settlementTxService.finalizeSettlementLedger(prepared.settlementId(), journalId);

    return getSettlement(businessId, finalized.settlementId());
  }

  public SettlementResponse getSettlement(UUID businessId, UUID settlementId) {
    SettlementEntity settlement = settlementRepository.findById(settlementId)
        .orElseThrow(() -> new BusinessException("SETTLEMENT_NOT_FOUND", "Settlement not found: " + settlementId));

    if (businessId != null && !settlement.getOrganizationId().equals(businessId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to settlement");
    }

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
