package com.banksystem.transaction.application.settlement;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SettlementDtos.CreateSplitRuleRequest;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementLegResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementPreviewRequest;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementPreviewResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SplitLegRequest;
import com.banksystem.transaction.api.dto.SettlementDtos.SplitLegResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SplitRuleResponse;
import com.banksystem.transaction.domain.settlement.BeneficiaryType;
import com.banksystem.transaction.domain.settlement.SettlementLegStatus;
import com.banksystem.transaction.domain.settlement.SettlementLegType;
import com.banksystem.transaction.domain.settlement.SplitRuleEntity;
import com.banksystem.transaction.domain.settlement.SplitRuleItemEntity;
import com.banksystem.transaction.domain.settlement.SplitRuleItemRepository;
import com.banksystem.transaction.domain.settlement.SplitRuleRepository;
import com.banksystem.transaction.domain.settlement.SplitType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SplitRuleService {

  private static final Logger log = LoggerFactory.getLogger(SplitRuleService.class);

  private final SplitRuleRepository splitRuleRepository;
  private final SplitRuleItemRepository splitRuleItemRepository;
  private final ObjectMapper objectMapper;

  public SplitRuleService(
      SplitRuleRepository splitRuleRepository,
      SplitRuleItemRepository splitRuleItemRepository,
      ObjectMapper objectMapper) {
    this.splitRuleRepository = splitRuleRepository;
    this.splitRuleItemRepository = splitRuleItemRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public SplitRuleResponse createSplitRule(UUID organizationId, CreateSplitRuleRequest request) {
    Instant now = Instant.now();
    SplitRuleEntity rule = SplitRuleEntity.create(organizationId, request.name().trim(), now);
    splitRuleRepository.save(rule);

    List<SplitRuleItemEntity> items = new ArrayList<>();
    int priority = 1;
    for (SplitLegRequest itemReq : request.items()) {
      SplitRuleItemEntity item = SplitRuleItemEntity.create(
          rule,
          itemReq.beneficiaryType(),
          itemReq.beneficiaryId(),
          itemReq.accountId(),
          itemReq.bankBin(),
          itemReq.accountNumber(),
          itemReq.beneficiaryName(),
          itemReq.splitType(),
          itemReq.value(),
          itemReq.priority() > 0 ? itemReq.priority() : priority++,
          now
      );
      items.add(item);
    }
    splitRuleItemRepository.saveAll(items);
    rule.setItems(items);

    log.info("[SPLIT-RULE] Created rule id={} with {} items for org={}", rule.getId(), items.size(), organizationId);
    return toResponse(rule);
  }

  @Transactional(readOnly = true)
  public List<SplitRuleResponse> listSplitRules(UUID organizationId) {
    return splitRuleRepository.findByOrganizationId(organizationId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public SplitRuleResponse getSplitRule(UUID organizationId, UUID ruleId) {
    SplitRuleEntity rule = splitRuleRepository.findById(ruleId).orElseThrow(() ->
        new BusinessException("SPLIT_RULE_NOT_FOUND", "Split rule not found"));
    if (organizationId != null && !rule.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to split rule");
    }
    return toResponse(rule);
  }

  @Transactional
  public void deleteSplitRule(UUID organizationId, UUID ruleId) {
    SplitRuleEntity rule = splitRuleRepository.findById(ruleId).orElseThrow(() ->
        new BusinessException("SPLIT_RULE_NOT_FOUND", "Split rule not found"));
    if (!rule.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to split rule");
    }
    splitRuleRepository.delete(rule);
  }

  public String serializeSnapshot(UUID splitRuleId, List<SplitLegRequest> customLegs) {
    List<SplitLegRequest> legsToSnapshot = new ArrayList<>();
    if (splitRuleId != null) {
      SplitRuleEntity rule = splitRuleRepository.findById(splitRuleId).orElseThrow(() ->
          new BusinessException("SPLIT_RULE_NOT_FOUND", "Split rule not found"));
      for (SplitRuleItemEntity item : rule.getItems()) {
        legsToSnapshot.add(new SplitLegRequest(
            item.getBeneficiaryType(), item.getBeneficiaryId(), item.getAccountId(),
            item.getBankBin(), item.getAccountNumber(), item.getBeneficiaryName(),
            item.getSplitType(), item.getValue(), item.getPriority()
        ));
      }
    } else if (customLegs != null && !customLegs.isEmpty()) {
      legsToSnapshot.addAll(customLegs);
    }

    if (legsToSnapshot.isEmpty()) {
      // Default: 100% remainder to merchant
      legsToSnapshot.add(new SplitLegRequest(
          BeneficiaryType.PLATFORM, "MERCHANT", null, null, null, "Merchant Collection",
          SplitType.REMAINDER, BigDecimal.ZERO, 1
      ));
    }

    try {
      return objectMapper.writeValueAsString(legsToSnapshot);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize split snapshot", ex);
    }
  }

  public List<ComputedLeg> calculateSplit(BigDecimal grossAmount, String snapshotJson, String currency) {
    List<SplitLegRequest> legs;
    try {
      if (snapshotJson != null && !snapshotJson.isBlank()) {
        legs = objectMapper.readValue(snapshotJson, new TypeReference<List<SplitLegRequest>>() {});
      } else {
        legs = List.of(new SplitLegRequest(
            BeneficiaryType.SELLER_INTERNAL, "DEFAULT", null, null, null, "Default Seller",
            SplitType.REMAINDER, BigDecimal.ZERO, 1
        ));
      }
    } catch (Exception ex) {
      log.error("[SPLIT-CALC] Failed to parse split snapshot: {}", ex.getMessage());
      legs = List.of(new SplitLegRequest(
          BeneficiaryType.SELLER_INTERNAL, "DEFAULT", null, null, null, "Default Seller",
          SplitType.REMAINDER, BigDecimal.ZERO, 1
      ));
    }

    List<SplitLegRequest> sorted = new ArrayList<>(legs);
    sorted.sort(Comparator.comparingInt(SplitLegRequest::priority));

    List<ComputedLeg> result = new ArrayList<>();
    BigDecimal allocatedSum = BigDecimal.ZERO;
    List<SplitLegRequest> remainderLegs = new ArrayList<>();

    // Pass 1: Fixed amounts and percentages
    for (SplitLegRequest leg : sorted) {
      if (leg.splitType() == SplitType.REMAINDER) {
        remainderLegs.add(leg);
        continue;
      }

      BigDecimal legAmount = BigDecimal.ZERO;
      if (leg.splitType() == SplitType.PERCENTAGE) {
        // Compute percentage using scale 0 for whole VND (or 2 for fractional)
        legAmount = grossAmount.multiply(leg.value())
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
      } else if (leg.splitType() == SplitType.FIXED_AMOUNT) {
        legAmount = leg.value();
      }

      if (allocatedSum.add(legAmount).compareTo(grossAmount) > 0) {
        legAmount = grossAmount.subtract(allocatedSum).max(BigDecimal.ZERO);
      }

      allocatedSum = allocatedSum.add(legAmount);
      SettlementLegType legType = determineLegType(leg);
      result.add(new ComputedLeg(leg, legAmount, legType));
    }

    // Pass 2: Remainder leg absorbs the exact difference down to last dong
    BigDecimal remaining = grossAmount.subtract(allocatedSum).max(BigDecimal.ZERO);
    if (!remainderLegs.isEmpty()) {
      SplitLegRequest primaryRemainder = remainderLegs.get(0);
      result.add(new ComputedLeg(primaryRemainder, remaining, determineLegType(primaryRemainder)));
    } else if (remaining.compareTo(BigDecimal.ZERO) > 0) {
      // If no remainder configured, assign remaining to first seller leg or platform
      if (!result.isEmpty()) {
        ComputedLeg first = result.get(0);
        result.set(0, new ComputedLeg(first.request(), first.amount().add(remaining), first.legType()));
      }
    }

    return result;
  }

  public SettlementPreviewResponse preview(SettlementPreviewRequest request) {
    String snapshot = serializeSnapshot(request.splitRuleId(), request.customLegs());
    List<ComputedLeg> computed = calculateSplit(request.grossAmount(), snapshot, "VND");

    BigDecimal commission = BigDecimal.ZERO;
    BigDecimal sellerNet = BigDecimal.ZERO;

    List<SettlementLegResponse> legResponses = new ArrayList<>();
    for (ComputedLeg c : computed) {
      if (c.legType() == SettlementLegType.COMMISSION) {
        commission = commission.add(c.amount());
      } else {
        sellerNet = sellerNet.add(c.amount());
      }
      legResponses.add(new SettlementLegResponse(
          null,
          c.request().beneficiaryType(),
          c.request().beneficiaryId(),
          c.request().accountId(),
          c.request().bankBin(),
          c.request().accountNumber(),
          c.request().beneficiaryName(),
          c.amount(),
          "VND",
          c.legType(),
          SettlementLegStatus.PENDING,
          null
      ));
    }

    return new SettlementPreviewResponse(request.grossAmount(), commission, sellerNet, legResponses);
  }

  private SettlementLegType determineLegType(SplitLegRequest leg) {
    if (leg.beneficiaryType() == BeneficiaryType.PLATFORM) {
      return SettlementLegType.COMMISSION;
    } else if (leg.beneficiaryType() == BeneficiaryType.SELLER_EXTERNAL || (leg.bankBin() != null && !leg.bankBin().isBlank())) {
      return SettlementLegType.EXTERNAL_PAYOUT;
    } else {
      return SettlementLegType.INTERNAL_CREDIT;
    }
  }

  private SplitRuleResponse toResponse(SplitRuleEntity rule) {
    List<SplitLegResponse> itemResponses = rule.getItems().stream()
        .map(i -> new SplitLegResponse(
            i.getId(), i.getBeneficiaryType(), i.getBeneficiaryId(), i.getAccountId(),
            i.getBankBin(), i.getAccountNumber(), i.getBeneficiaryName(),
            i.getSplitType(), i.getValue(), i.getPriority()
        ))
        .toList();
    return new SplitRuleResponse(rule.getId(), rule.getOrganizationId(), rule.getName(), rule.getStatus(), itemResponses, rule.getCreatedAt());
  }

  public record ComputedLeg(
      SplitLegRequest request,
      BigDecimal amount,
      SettlementLegType legType
  ) {}
}
