package com.banksystem.transaction.application.risk;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.api.PageResponse;
import com.banksystem.transaction.api.dto.RiskDtos.BlacklistRequest;
import com.banksystem.transaction.api.dto.RiskDtos.BlacklistResponse;
import com.banksystem.transaction.api.dto.RiskDtos.RiskRuleRequest;
import com.banksystem.transaction.api.dto.RiskDtos.RiskRuleResponse;
import com.banksystem.transaction.application.transfer.impl.TransferSagaOrchestrator;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.risk.RiskBlacklistEntity;
import com.banksystem.transaction.domain.risk.RiskBlacklistRepository;
import com.banksystem.transaction.domain.risk.RiskRuleEntity;
import com.banksystem.transaction.domain.risk.RiskRuleRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
public class RiskAdminService {

  private static final Set<String> RULE_TYPES =
      Set.of("AMOUNT", "VELOCITY_COUNT", "VELOCITY_TOTAL");
  private static final Set<String> ACTIONS = Set.of("ALLOW", "ALERT", "REVIEW", "BLOCK");
  private static final Set<String> SUBJECT_TYPES = Set.of("USER", "ACCOUNT", "BANK");

  private final RiskRuleRepository ruleRepository;
  private final RiskBlacklistRepository blacklistRepository;
  private final TransferOrderRepository transferRepository;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final AuditLogRepository auditRepository;

  public RiskAdminService(
      RiskRuleRepository ruleRepository,
      RiskBlacklistRepository blacklistRepository,
      TransferOrderRepository transferRepository,
      TransferSagaOrchestrator sagaOrchestrator,
      AuditLogRepository auditRepository) {
    this.ruleRepository = ruleRepository;
    this.blacklistRepository = blacklistRepository;
    this.transferRepository = transferRepository;
    this.sagaOrchestrator = sagaOrchestrator;
    this.auditRepository = auditRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<RiskRuleResponse> rules(RiskListQuery query) {
    Page<RiskRuleEntity> page = ruleRepository.findAllByOrderByPriorityAsc(
        PageRequest.of(query.page(), query.size()));
    return new PageResponse<>(page.getContent().stream().map(this::ruleResponse).toList(),
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional
  public RiskRuleResponse saveRule(UUID id, RiskRuleRequest request) {
    String code = upper(request.code());
    String type = allowed(upper(request.ruleType()), RULE_TYPES, "RISK_RULE_TYPE_INVALID");
    String action = allowed(upper(request.action()), ACTIONS, "RISK_ACTION_INVALID");
    RiskRuleEntity rule = id == null
        ? ruleRepository.findByCode(code).orElseGet(RiskRuleEntity::new)
        : ruleRepository.findById(id)
            .orElseThrow(() -> new BusinessException("RISK_RULE_NOT_FOUND", "Risk rule not found"));
    Instant now = Instant.now();
    if (rule.getId() == null) {
      rule.setId(UUID.randomUUID());
      rule.setCreatedAt(now);
    }
    rule.setCode(code);
    rule.setRuleType(type);
    rule.setAction(action);
    rule.setEnabled(request.enabled());
    rule.setPriority(request.priority());
    rule.setThresholdAmount(request.thresholdAmount());
    rule.setWindowSeconds(request.windowSeconds());
    rule.setMaxCount(request.maxCount());
    rule.setMaxTotalAmount(request.maxTotalAmount());
    rule.setDescription(trim(request.description(), 255));
    rule.setUpdatedAt(now);
    validateRule(rule);
    return ruleResponse(ruleRepository.save(rule));
  }

  @Transactional(readOnly = true)
  public PageResponse<BlacklistResponse> blacklist(RiskListQuery query) {
    Page<RiskBlacklistEntity> page = blacklistRepository.findAllByOrderByCreatedAtDesc(
        PageRequest.of(query.page(), query.size()));
    return new PageResponse<>(page.getContent().stream().map(this::blacklistResponse).toList(),
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional
  public BlacklistResponse addBlacklist(BlacklistRequest request, UUID actorId) {
    String type = allowed(upper(request.subjectType()), SUBJECT_TYPES, "BLACKLIST_TYPE_INVALID");
    Instant now = Instant.now();
    RiskBlacklistEntity item = new RiskBlacklistEntity();
    item.setId(UUID.randomUUID());
    item.setSubjectType(type);
    item.setSubjectValue(request.subjectValue().trim());
    item.setReason(trim(request.reason(), 500));
    item.setActive(true);
    item.setExpiresAt(request.expiresAt());
    item.setCreatedBy(actorId);
    item.setCreatedAt(now);
    item.setUpdatedAt(now);
    return blacklistResponse(blacklistRepository.save(item));
  }

  @Transactional
  public BlacklistResponse deactivateBlacklist(UUID id) {
    RiskBlacklistEntity item = blacklistRepository.findById(id)
        .orElseThrow(() -> new BusinessException("BLACKLIST_NOT_FOUND", "Blacklist entry not found"));
    item.setActive(false);
    item.setUpdatedAt(Instant.now());
    return blacklistResponse(blacklistRepository.save(item));
  }

  public TransferOrderEntity approveTransfer(UUID transferId, UUID actorId, String note, String ip) {
    TransferOrderEntity order = requireRiskReview(transferId);
    order.setRiskDecision("APPROVED");
    order.setRiskReason(trim(note, 500));
    order.setFailureReason(null);
    order.setStatus(TransferStatus.PENDING);
    order.setUpdatedAt(Instant.now());
    transferRepository.save(order);
    auditRepository.save(AuditLogEntity.of(
        actorId, "RISK_TRANSFER_APPROVE", "TRANSFER", transferId.toString(), ip,
        trim(note, 500)));
    return sagaOrchestrator.run(order);
  }

  @Transactional
  public TransferOrderEntity rejectTransfer(UUID transferId, UUID actorId, String note, String ip) {
    TransferOrderEntity order = requireRiskReview(transferId);
    order.setRiskDecision("REJECTED");
    order.setRiskReason(trim(note, 500));
    order.setFailureReason("RISK_REJECTED" + (note == null || note.isBlank() ? "" : ": " + trim(note, 450)));
    order.setStatus(TransferStatus.FAILED);
    order.setUpdatedAt(Instant.now());
    transferRepository.save(order);
    auditRepository.save(AuditLogEntity.of(
        actorId, "RISK_TRANSFER_REJECT", "TRANSFER", transferId.toString(), ip,
        trim(note, 500)));
    return order;
  }

  private TransferOrderEntity requireRiskReview(UUID id) {
    TransferOrderEntity order = transferRepository.findById(id)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    if (order.getStatus() != TransferStatus.RISK_REVIEW) {
      throw new BusinessException("INVALID_TRANSFER_STATE", "Transfer is not awaiting risk review");
    }
    return order;
  }

  private void validateRule(RiskRuleEntity rule) {
    if ("AMOUNT".equals(rule.getRuleType()) && rule.getThresholdAmount() == null) {
      throw new BusinessException("RISK_THRESHOLD_REQUIRED", "thresholdAmount is required");
    }
    if (rule.getRuleType().startsWith("VELOCITY") && rule.getWindowSeconds() == null) {
      throw new BusinessException("RISK_WINDOW_REQUIRED", "windowSeconds is required");
    }
    if ("VELOCITY_COUNT".equals(rule.getRuleType()) && rule.getMaxCount() == null) {
      throw new BusinessException("RISK_MAX_COUNT_REQUIRED", "maxCount is required");
    }
    if ("VELOCITY_TOTAL".equals(rule.getRuleType()) && rule.getMaxTotalAmount() == null) {
      throw new BusinessException("RISK_MAX_TOTAL_REQUIRED", "maxTotalAmount is required");
    }
  }

  private RiskRuleResponse ruleResponse(RiskRuleEntity rule) {
    return new RiskRuleResponse(
        rule.getId().toString(), rule.getCode(), rule.getRuleType(), rule.getAction(),
        rule.isEnabled(), rule.getPriority(), rule.getThresholdAmount(), rule.getWindowSeconds(),
        rule.getMaxCount(), rule.getMaxTotalAmount(), rule.getDescription(),
        rule.getCreatedAt(), rule.getUpdatedAt());
  }

  private BlacklistResponse blacklistResponse(RiskBlacklistEntity item) {
    return new BlacklistResponse(
        item.getId().toString(), item.getSubjectType(), item.getSubjectValue(), item.getReason(),
        item.isActive(), item.getExpiresAt(), item.getCreatedBy().toString(),
        item.getCreatedAt(), item.getUpdatedAt());
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static String allowed(String value, Set<String> allowed, String code) {
    if (!allowed.contains(value)) {
      throw new BusinessException(code, "Unsupported value: " + value);
    }
    return value;
  }

  private static String trim(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String result = value.trim();
    return result.length() <= max ? result : result.substring(0, max);
  }
}
