package com.banksystem.transaction.application.settlement;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementFinalizedContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementLegPreparedContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementPreparedContext;
import com.banksystem.transaction.application.settlement.SplitRuleService.ComputedLeg;
import com.banksystem.transaction.domain.collection.CollectionOrderEntity;
import com.banksystem.transaction.domain.collection.CollectionOrderRepository;
import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import com.banksystem.transaction.domain.merchant.MerchantAccountEntity;
import com.banksystem.transaction.domain.merchant.MerchantAccountRepository;
import com.banksystem.transaction.domain.settlement.B2bPayoutEntity;
import com.banksystem.transaction.domain.settlement.B2bPayoutRepository;
import com.banksystem.transaction.domain.settlement.B2bPayoutStatus;
import com.banksystem.transaction.domain.settlement.BeneficiaryType;
import com.banksystem.transaction.domain.settlement.SettlementEntity;
import com.banksystem.transaction.domain.settlement.SettlementLegEntity;
import com.banksystem.transaction.domain.settlement.SettlementLegRepository;
import com.banksystem.transaction.domain.settlement.SettlementLegStatus;
import com.banksystem.transaction.domain.settlement.SettlementLegType;
import com.banksystem.transaction.domain.settlement.SettlementRepository;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import com.banksystem.transaction.infrastructure.outbox.OutboxService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementTransactionService {

  private static final Logger log = LoggerFactory.getLogger(SettlementTransactionService.class);

  private final CollectionOrderRepository collectionOrderRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementLegRepository settlementLegRepository;
  private final B2bPayoutRepository b2bPayoutRepository;
  private final MerchantAccountRepository merchantAccountRepository;
  private final SplitRuleService splitRuleService;
  private final OutboxService outboxService;

  public SettlementTransactionService(
      CollectionOrderRepository collectionOrderRepository,
      SettlementRepository settlementRepository,
      SettlementLegRepository settlementLegRepository,
      B2bPayoutRepository b2bPayoutRepository,
      MerchantAccountRepository merchantAccountRepository,
      SplitRuleService splitRuleService,
      OutboxService outboxService) {
    this.collectionOrderRepository = collectionOrderRepository;
    this.settlementRepository = settlementRepository;
    this.settlementLegRepository = settlementLegRepository;
    this.b2bPayoutRepository = b2bPayoutRepository;
    this.merchantAccountRepository = merchantAccountRepository;
    this.splitRuleService = splitRuleService;
    this.outboxService = outboxService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public SettlementPreparedContext prepareSettlement(UUID businessId, UUID orderId) {
    Instant now = Instant.now();

    CollectionOrderEntity order = collectionOrderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Collection order not found: " + orderId));

    if (businessId != null && !order.getOrganizationId().equals(businessId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to collection order: " + orderId);
    }

    if (order.getStatus() != CollectionOrderStatus.PAID && order.getStatus() != CollectionOrderStatus.OVERPAID) {
      throw new BusinessException("ORDER_NOT_SETTLEABLE", "Order must be PAID or OVERPAID to settle. Current: " + order.getStatus());
    }

    MerchantAccountEntity merchant = merchantAccountRepository.findByOrganizationId(order.getOrganizationId())
        .orElseThrow(() -> new BusinessException("MERCHANT_NOT_CONFIGURED", "Merchant account config not found for organization: " + order.getOrganizationId()));

    if (merchant.getEscrowAccountId() == null) {
      throw new BusinessException("ESCROW_ACCOUNT_MISSING", "Merchant escrow account is not configured");
    }

    // OVERPAID handling: only split expected amount, hold overpaid excess in suspense
    BigDecimal settleableAmount = order.getExpectedAmount();
    BigDecimal overpaidAmount = order.getPaidAmount().subtract(order.getExpectedAmount()).max(BigDecimal.ZERO);

    UUID settlementId = UUID.nameUUIDFromBytes(("SETTLEMENT:" + order.getId()).getBytes(StandardCharsets.UTF_8));
    String commandId = "SETTLEMENT:" + settlementId;
    String requestHash = calculateRequestHash(order);

    Optional<SettlementEntity> existingOpt = settlementRepository.findByCollectionOrderIdForUpdate(order.getId());
    if (existingOpt.isPresent()) {
      SettlementEntity existing = existingOpt.get();
      if (existing.getStatus() == SettlementStatus.COMPLETED
          || existing.getStatus() == SettlementStatus.REVERSED) {
        return toPreparedContext(existing, merchant.getEscrowAccountId());
      }
      boolean recoverableLegacyPosting = existing.getRequestHash().startsWith("LEGACY:")
          && existing.getStatus() == SettlementStatus.LEDGER_POSTED
          && existing.getLedgerJournalId() != null;
      if (!existing.getRequestHash().equals(requestHash) && !recoverableLegacyPosting) {
        existing.setStatus(SettlementStatus.MANUAL_REVIEW);
        existing.setFailureReason("Settlement request hash mismatch with order data");
        existing.setUpdatedAt(now);
        settlementRepository.save(existing);
        throw new BusinessException("SETTLEMENT_CONFLICT", "Settlement data mismatch with existing record");
      }
      return toPreparedContext(existing, merchant.getEscrowAccountId());
    }

    // Split rule calculation on settleable amount
    List<ComputedLeg> computed = splitRuleService.calculateSplit(settleableAmount, order.getSplitRuleSnapshot(), order.getCurrency());

    // Sort legs stably to guarantee deterministic ordinals
    List<ComputedLeg> sorted = new ArrayList<>(computed);
    sorted.sort(Comparator.comparingInt((ComputedLeg c) -> c.request().priority())
        .thenComparingInt(c -> c.legType().ordinal())
        .thenComparingInt(c -> c.request().beneficiaryType().ordinal())
        .thenComparing(c -> c.request().beneficiaryId() != null ? c.request().beneficiaryId() : "")
        .thenComparing(ComputedLeg::amount));

    BigDecimal platformCommission = BigDecimal.ZERO;
    BigDecimal sellerNet = BigDecimal.ZERO;

    for (ComputedLeg c : sorted) {
      if (c.legType() == SettlementLegType.COMMISSION) {
        platformCommission = platformCommission.add(c.amount());
      } else {
        sellerNet = sellerNet.add(c.amount());
      }
    }

    SettlementEntity settlement = SettlementEntity.create(
        settlementId, order.getOrganizationId(), order.getId(),
        commandId, requestHash, settleableAmount, overpaidAmount,
        platformCommission, sellerNet, order.getCurrency(), now
    );
    settlement.setStatus(SettlementStatus.LEDGER_PENDING);
    settlementRepository.save(settlement);

    List<SettlementLegEntity> legEntities = new ArrayList<>();
    int ordinal = 1;

    for (ComputedLeg c : sorted) {
      UUID legAccountId = c.request().accountId();
      if (c.legType() == SettlementLegType.COMMISSION) {
        legAccountId = merchant.getCommissionAccountId();
      }

      String legKey = "LEG:" + settlementId + ":" + ordinal + ":" + c.request().beneficiaryType().name() + ":"
          + (c.request().beneficiaryId() != null ? c.request().beneficiaryId() : "NONE");

      SettlementLegEntity legEntity = SettlementLegEntity.create(
          settlement,
          legKey,
          c.request().beneficiaryType(),
          c.request().beneficiaryId(),
          legAccountId,
          c.request().bankBin(),
          c.request().accountNumber(),
          c.request().beneficiaryName(),
          c.amount(),
          order.getCurrency(),
          c.legType(),
          now
      );
      legEntities.add(legEntity);
      ordinal++;
    }

    if (overpaidAmount.compareTo(BigDecimal.ZERO) > 0) {
      SettlementLegEntity overpaymentHold = SettlementLegEntity.create(
          settlement,
          "LEG:" + settlementId + ":OVERPAYMENT_HOLD",
          BeneficiaryType.CUSTOMER,
          order.getCustomerReference(),
          null,
          null,
          null,
          "Customer overpayment suspense",
          overpaidAmount,
          order.getCurrency(),
          SettlementLegType.OVERPAYMENT_HOLD,
          now
      );
      legEntities.add(overpaymentHold);
    }

    settlementLegRepository.saveAll(legEntities);
    settlement.setLegs(legEntities);

    return toPreparedContext(settlement, merchant.getEscrowAccountId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public SettlementFinalizedContext finalizeSettlementLedger(UUID settlementId, UUID ledgerJournalId) {
    Instant now = Instant.now();

    SettlementEntity settlement = settlementRepository.findByIdForUpdate(settlementId)
        .orElseThrow(() -> new BusinessException("SETTLEMENT_NOT_FOUND", "Settlement not found: " + settlementId));

    if (settlement.getStatus() == SettlementStatus.PAYOUT_PENDING
        || settlement.getStatus() == SettlementStatus.COMPLETED) {
      List<UUID> existingPayoutLegIds = settlement.getLegs().stream()
          .filter(l -> l.getLegType() == SettlementLegType.EXTERNAL_PAYOUT)
          .map(SettlementLegEntity::getId)
          .toList();
      return new SettlementFinalizedContext(
          settlement.getId(), settlement.getOrganizationId(), settlement.getCollectionOrderId(),
          settlement.getStatus(), settlement.getLedgerJournalId(), existingPayoutLegIds
      );
    }

    settlement.setLedgerJournalId(ledgerJournalId);
    settlement.setUpdatedAt(now);

    List<UUID> payoutLegIds = new ArrayList<>();

    for (SettlementLegEntity leg : settlement.getLegs()) {
      if (leg.getLegType() == SettlementLegType.COMMISSION
          || leg.getLegType() == SettlementLegType.INTERNAL_CREDIT
          || leg.getLegType() == SettlementLegType.OVERPAYMENT_HOLD) {
        leg.setStatus(SettlementLegStatus.COMPLETED);
        leg.setUpdatedAt(now);
      } else if (leg.getLegType() == SettlementLegType.EXTERNAL_PAYOUT) {
        // External leg: persist durable payout before scheduling worker
        UUID payoutId = UUID.nameUUIDFromBytes(("PAYOUT:" + leg.getId()).getBytes(StandardCharsets.UTF_8));
        String clientRequestId = "NAPAS_PAYOUT:" + payoutId;

        Optional<B2bPayoutEntity> existingPayout = b2bPayoutRepository.findBySettlementLegId(leg.getId());
        if (existingPayout.isEmpty()) {
          B2bPayoutEntity payout = B2bPayoutEntity.create(
              payoutId, clientRequestId, settlement.getOrganizationId(), leg.getId(),
              "NAPAS_247", leg.getAmount(), leg.getCurrency(), leg.getAccountId(),
              leg.getBankBin(), leg.getAccountNumber(), leg.getBeneficiaryName(), now
          );
          b2bPayoutRepository.save(payout);
        }
        leg.setPayoutId(payoutId);
        leg.setStatus(SettlementLegStatus.PROCESSING);
        leg.setUpdatedAt(now);
        payoutLegIds.add(leg.getId());
      }
    }

    settlementLegRepository.saveAll(settlement.getLegs());

    if (payoutLegIds.isEmpty()) {
      settlement.setStatus(SettlementStatus.COMPLETED);
      outboxService.enqueue(
          "SETTLEMENT", "settlement.completed.v1", settlement.getId(), "completed",
          Map.of(
              "settlementId", settlement.getId().toString(),
              "collectionOrderId", settlement.getCollectionOrderId().toString(),
              "organizationId", settlement.getOrganizationId().toString(),
              "grossAmount", settlement.getGrossAmount(),
              "status", "COMPLETED",
              "completedAt", now.toString()
          )
      );
      log.info("[SETTLEMENT-FINALIZED] Settlement [{}] COMPLETED (all legs internal)", settlement.getId());
    } else {
      settlement.setStatus(SettlementStatus.PAYOUT_PENDING);
      log.info("[SETTLEMENT-FINALIZED] Settlement [{}] PAYOUT_PENDING ({} external legs scheduled)",
          settlement.getId(), payoutLegIds.size());
    }

    settlementRepository.save(settlement);

    return new SettlementFinalizedContext(
        settlement.getId(), settlement.getOrganizationId(), settlement.getCollectionOrderId(),
        settlement.getStatus(), ledgerJournalId, payoutLegIds
    );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeSettlementIfAllLegsDone(UUID settlementId) {
    Instant now = Instant.now();

    SettlementEntity settlement = settlementRepository.findByIdForUpdate(settlementId).orElse(null);
    if (settlement == null || settlement.getStatus() == SettlementStatus.COMPLETED) {
      return;
    }

    boolean allCompleted = settlement.getLegs().stream()
        .allMatch(l -> l.getStatus() == SettlementLegStatus.COMPLETED);

    if (allCompleted) {
      settlement.setStatus(SettlementStatus.COMPLETED);
      settlement.setUpdatedAt(now);
      settlementRepository.save(settlement);

      outboxService.enqueue(
          "SETTLEMENT", "settlement.completed.v1", settlement.getId(), "completed",
          Map.of(
              "settlementId", settlement.getId().toString(),
              "collectionOrderId", settlement.getCollectionOrderId().toString(),
              "organizationId", settlement.getOrganizationId().toString(),
              "grossAmount", settlement.getGrossAmount(),
              "status", "COMPLETED",
              "completedAt", now.toString()
          )
      );
      log.info("[SETTLEMENT-COMPLETED] Settlement [{}] all legs COMPLETED -> settlement COMPLETED", settlement.getId());
    }
  }

  private SettlementPreparedContext toPreparedContext(SettlementEntity settlement, UUID escrowAccountId) {
    List<SettlementLegPreparedContext> legContexts = settlement.getLegs().stream()
        .sorted(Comparator.comparing(SettlementLegEntity::getLegKey))
        .map(l -> new SettlementLegPreparedContext(
            l.getId(), l.getLegKey(), l.getBeneficiaryType(), l.getBeneficiaryId(),
            l.getAccountId(), l.getBankBin(), l.getAccountNumber(), l.getBeneficiaryName(),
            l.getAmount(), l.getCurrency(), l.getLegType(), l.getStatus(),
            l.getLegType() == SettlementLegType.COMMISSION ? "COMMISSION:PLATFORM"
                : (l.getLegType() == SettlementLegType.INTERNAL_CREDIT ? "SELLER_NET:INTERNAL"
                    : (l.getLegType() == SettlementLegType.OVERPAYMENT_HOLD
                        ? "LIABILITY:CUSTOMER_OVERPAYMENT" : "SELLER_NET:EXTERNAL"))
        ))
        .toList();

    return new SettlementPreparedContext(
        settlement.getId(), settlement.getOrganizationId(), settlement.getCollectionOrderId(),
        escrowAccountId, settlement.getGrossAmount(), settlement.getOverpaidAmount(),
        settlement.getPlatformCommission(), settlement.getSellerNetAmount(), settlement.getCurrency(),
        settlement.getCommandId(), settlement.getRequestHash(), settlement.getLedgerJournalId(),
        settlement.getStatus(), legContexts
    );
  }

  private String calculateRequestHash(CollectionOrderEntity order) {
    try {
      String raw = order.getId() + ":" + order.getExpectedAmount() + ":" + order.getPaidAmount() + ":" + (order.getSplitRuleSnapshot() != null ? order.getSplitRuleSnapshot() : "");
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
