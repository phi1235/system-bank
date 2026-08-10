package com.banksystem.transaction.application.risk;

import com.banksystem.transaction.application.reconciliation.OpsAlertPublisher;
import com.banksystem.transaction.domain.risk.RiskAssessmentEntity;
import com.banksystem.transaction.domain.risk.RiskAssessmentRepository;
import com.banksystem.transaction.domain.risk.RiskBlacklistEntity;
import com.banksystem.transaction.domain.risk.RiskBlacklistRepository;
import com.banksystem.transaction.domain.risk.RiskRuleEntity;
import com.banksystem.transaction.domain.risk.RiskRuleRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskEngine {

  private static final List<TransferStatus> EXCLUDED_VELOCITY_STATUSES =
      List.of(TransferStatus.FAILED, TransferStatus.COMPENSATED);

  private final RiskRuleRepository ruleRepository;
  private final RiskBlacklistRepository blacklistRepository;
  private final RiskAssessmentRepository assessmentRepository;
  private final TransferOrderRepository transferRepository;
  private final OpsAlertPublisher opsAlertPublisher;

  public RiskEngine(
      RiskRuleRepository ruleRepository,
      RiskBlacklistRepository blacklistRepository,
      RiskAssessmentRepository assessmentRepository,
      TransferOrderRepository transferRepository,
      OpsAlertPublisher opsAlertPublisher) {
    this.ruleRepository = ruleRepository;
    this.blacklistRepository = blacklistRepository;
    this.assessmentRepository = assessmentRepository;
    this.transferRepository = transferRepository;
    this.opsAlertPublisher = opsAlertPublisher;
  }

  @Transactional
  public RiskResult assess(TransferOrderEntity order) {
    List<String> matched = new ArrayList<>();
    String decision = "ALLOW";
    int score = 0;
    String reason = null;

    Optional<RiskBlacklistEntity> blacklist = findBlacklist(order);
    if (blacklist.isPresent()) {
      decision = "BLOCK";
      score = 100;
      reason = blacklist.get().getReason();
      matched.add("BLACKLIST_" + blacklist.get().getSubjectType());
    } else {
      for (RiskRuleEntity rule : ruleRepository.findByEnabledTrueOrderByPriorityAsc()) {
        if (!matches(rule, order)) {
          continue;
        }
        matched.add(rule.getCode());
        int ruleScore = score(rule.getAction());
        if (ruleScore > score) {
          score = ruleScore;
          decision = normalizeAction(rule.getAction());
          reason = rule.getDescription();
        }
      }
    }

    RiskAssessmentEntity assessment = new RiskAssessmentEntity();
    assessment.setId(UUID.randomUUID());
    assessment.setTransferId(order.getId());
    assessment.setUserId(order.getUserId());
    assessment.setDecision(decision);
    assessment.setScore(score);
    assessment.setMatchedRules(String.join(",", matched));
    assessment.setReason(reason);
    assessment.setCreatedAt(Instant.now());
    assessmentRepository.save(assessment);

    order.setRiskDecision(decision);
    order.setRiskScore(score);
    order.setRiskReason(reason);
    transferRepository.save(order);
    if (!"ALLOW".equals(decision)) {
      opsAlertPublisher.riskDetected(order, decision, score, matched, reason);
    }
    return new RiskResult(decision, score, matched, reason);
  }

  private Optional<RiskBlacklistEntity> findBlacklist(TransferOrderEntity order) {
    Instant now = Instant.now();
    Optional<RiskBlacklistEntity> result = blacklistRepository.findActive(
        "USER", order.getUserId().toString(), now);
    if (result.isPresent()) return result;
    result = blacklistRepository.findActive("ACCOUNT", order.getToAccountNumber(), now);
    if (result.isPresent()) return result;
    if (order.getTargetBankCode() != null) {
      return blacklistRepository.findActive("BANK", order.getTargetBankCode(), now);
    }
    return Optional.empty();
  }

  private boolean matches(RiskRuleEntity rule, TransferOrderEntity order) {
    return switch (rule.getRuleType()) {
      case "AMOUNT" -> rule.getThresholdAmount() != null
          && order.getAmount().compareTo(rule.getThresholdAmount()) >= 0;
      case "VELOCITY_COUNT" -> rule.getWindowSeconds() != null && rule.getMaxCount() != null
          && transferRepository.countRiskVelocity(
              order.getUserId(), Instant.now().minusSeconds(rule.getWindowSeconds()),
              EXCLUDED_VELOCITY_STATUSES) >= rule.getMaxCount();
      case "VELOCITY_TOTAL" -> rule.getWindowSeconds() != null && rule.getMaxTotalAmount() != null
          && velocityTotal(order, rule).compareTo(rule.getMaxTotalAmount()) > 0;
      default -> false;
    };
  }

  private BigDecimal velocityTotal(TransferOrderEntity order, RiskRuleEntity rule) {
    return transferRepository.sumRiskVelocity(
        order.getUserId(), Instant.now().minusSeconds(rule.getWindowSeconds()),
        EXCLUDED_VELOCITY_STATUSES);
  }

  private int score(String action) {
    return switch (normalizeAction(action)) {
      case "BLOCK" -> 100;
      case "REVIEW" -> 70;
      case "ALERT" -> 30;
      default -> 0;
    };
  }

  private String normalizeAction(String action) {
    if (action == null) return "ALLOW";
    return switch (action.trim().toUpperCase()) {
      case "BLOCK", "REVIEW", "ALERT" -> action.trim().toUpperCase();
      default -> "ALLOW";
    };
  }

  public record RiskResult(String decision, int score, List<String> matchedRules, String reason) {}
}
