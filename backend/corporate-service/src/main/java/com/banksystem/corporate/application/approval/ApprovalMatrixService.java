package com.banksystem.corporate.application.approval;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.ApprovalPolicyResponse;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.CreateApprovalPolicyRequest;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.CreateApprovalStepTemplateRequest;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.CreateApprovalTierRequest;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.SimulateApprovalPlanResponse;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.SimulatedStep;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.StepTemplateResponse;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.TierResponse;
import com.banksystem.corporate.application.audit.CorporateAuditService;
import com.banksystem.corporate.application.corporation.CorporationService;
import com.banksystem.corporate.domain.approval.ApprovalPolicyEntity;
import com.banksystem.corporate.domain.approval.ApprovalPolicyRepository;
import com.banksystem.corporate.domain.approval.ApprovalStepTemplateEntity;
import com.banksystem.corporate.domain.approval.ApprovalTierEntity;
import com.banksystem.corporate.domain.corporation.CorporateMembershipEntity;
import com.banksystem.corporate.domain.corporation.CorporateMembershipRepository;
import com.banksystem.corporate.domain.corporation.CorporationEntity;
import com.banksystem.corporate.domain.corporation.CorporationRepository;
import java.math.BigDecimal;
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
public class ApprovalMatrixService {

  private static final Logger log = LoggerFactory.getLogger(ApprovalMatrixService.class);

  private final ApprovalPolicyRepository policyRepository;
  private final CorporationRepository corporationRepository;
  private final CorporateMembershipRepository membershipRepository;
  private final CorporationService corporationService;
  private final CorporateAuditService auditService;

  public ApprovalMatrixService(
      ApprovalPolicyRepository policyRepository,
      CorporationRepository corporationRepository,
      CorporateMembershipRepository membershipRepository,
      CorporationService corporationService,
      CorporateAuditService auditService) {
    this.policyRepository = policyRepository;
    this.corporationRepository = corporationRepository;
    this.membershipRepository = membershipRepository;
    this.corporationService = corporationService;
    this.auditService = auditService;
  }

  @Transactional
  public ApprovalPolicyResponse createPolicy(UUID corporateId, UUID userId, CreateApprovalPolicyRequest req) {
    corporationService.validateAdminOrRole(corporateId, userId, "CORPORATE_ADMIN");

    CorporationEntity corp = corporationRepository.findByIdForUpdate(corporateId).orElseThrow(() ->
        new BusinessException("CORPORATION_NOT_FOUND", "Corporation not found"));

    if (req.tiers() == null || req.tiers().isEmpty()) {
      throw new BusinessException("INVALID_POLICY", "Approval policy must contain at least one tier");
    }
    if (req.effectiveFrom() != null
        && req.effectiveTo() != null
        && !req.effectiveTo().isAfter(req.effectiveFrom())) {
      throw new BusinessException(
          "INVALID_POLICY_EFFECTIVE_RANGE", "effectiveTo must be after effectiveFrom");
    }

    // Sort and validate tier ranges [minAmount, maxAmount) for continuity
    List<CreateApprovalTierRequest> sortedTiers = req.tiers().stream()
        .sorted(Comparator.comparing(CreateApprovalTierRequest::minAmount))
        .toList();

    validateTiersAndSteps(corporateId, sortedTiers);

    Integer maxVersion = policyRepository.findMaxVersionNumber(corporateId);
    int nextVersion = (maxVersion == null) ? 1 : maxVersion + 1;

    ApprovalPolicyEntity policy = new ApprovalPolicyEntity();
    policy.setId(UUID.randomUUID());
    policy.setCorporation(corp);
    policy.setCorporateId(corporateId);
    policy.setPolicyName(req.policyName().trim());
    policy.setVersionNumber(nextVersion);
    policy.setStatus("DRAFT");
    String policyCurrency = req.currency() != null ? req.currency().trim().toUpperCase() : "VND";
    if (!policyCurrency.matches("[A-Z]{3}")) {
      throw new BusinessException("INVALID_CURRENCY", "Currency must be a three-letter ISO code");
    }
    policy.setCurrency(policyCurrency);
    policy.setAllowSelfApproval(req.allowSelfApproval());
    policy.setRequireRoleSeparation(req.requireRoleSeparation());
    policy.setEffectiveFrom(req.effectiveFrom());
    policy.setEffectiveTo(req.effectiveTo());
    policy.setCreatedBy(userId);
    policy.setCreatedAt(Instant.now());
    policy.setUpdatedAt(Instant.now());

    List<ApprovalTierEntity> tiers = new ArrayList<>();
    int priority = 1;
    for (CreateApprovalTierRequest tierReq : sortedTiers) {
      ApprovalTierEntity tier = new ApprovalTierEntity();
      tier.setId(UUID.randomUUID());
      tier.setPolicy(policy);
      tier.setTierName(tierReq.tierName().trim());
      tier.setMinAmount(tierReq.minAmount());
      tier.setMaxAmount(tierReq.maxAmount());
      tier.setPriorityOrder(priority++);
      tier.setCreatedAt(Instant.now());

      List<ApprovalStepTemplateEntity> steps = new ArrayList<>();
      for (CreateApprovalStepTemplateRequest stepReq : tierReq.steps()) {
        ApprovalStepTemplateEntity step = new ApprovalStepTemplateEntity();
        step.setId(UUID.randomUUID());
        step.setTier(tier);
        step.setStepOrder(stepReq.stepOrder());
        step.setStepName(stepReq.stepName().trim());
        step.setRequiredRole(stepReq.requiredRole().trim().toUpperCase());
        step.setMinApprovals(stepReq.minApprovals() > 0 ? stepReq.minApprovals() : 1);
        step.setAuthMethod(stepReq.authMethod() != null ? stepReq.authMethod().toUpperCase() : "STANDARD");
        step.setDeadlineHours(stepReq.deadlineHours());
        step.setCreatedAt(Instant.now());
        steps.add(step);
      }
      tier.setSteps(steps);
      tiers.add(tier);
    }
    policy.setTiers(tiers);

    ApprovalPolicyEntity saved = policyRepository.save(policy);
    auditService.log(corporateId, userId, "CREATE_APPROVAL_POLICY", "APPROVAL_POLICY", saved.getId().toString(), "Version=" + nextVersion);
    return toResponse(saved);
  }

  private void validateTiersAndSteps(UUID corporateId, List<CreateApprovalTierRequest> sortedTiers) {
    if (sortedTiers.get(0).minAmount().compareTo(BigDecimal.ZERO) != 0) {
      throw new BusinessException("INVALID_TIER_RANGE", "The first approval tier must start at minAmount = 0");
    }

    for (int i = 0; i < sortedTiers.size(); i++) {
      CreateApprovalTierRequest tier = sortedTiers.get(i);
      if (tier.minAmount().compareTo(BigDecimal.ZERO) < 0) {
        throw new BusinessException("INVALID_TIER_AMOUNT", "minAmount cannot be negative");
      }
      if (tier.maxAmount() != null && tier.maxAmount().compareTo(tier.minAmount()) <= 0) {
        throw new BusinessException("INVALID_TIER_RANGE", "maxAmount must be strictly greater than minAmount in tier: " + tier.tierName());
      }
      if (i > 0) {
        BigDecimal prevMax = sortedTiers.get(i - 1).maxAmount();
        if (prevMax == null || tier.minAmount().compareTo(prevMax) != 0) {
          throw new BusinessException("TIER_GAP_OR_OVERLAP", "Tier range gap or overlap detected between tier " + (i) + " and tier " + (i + 1));
        }
      }
      if (i < sortedTiers.size() - 1 && tier.maxAmount() == null) {
        throw new BusinessException("INVALID_TIER_RANGE", "Only the last approval tier can have unbounded maxAmount (null)");
      }
      if (i == sortedTiers.size() - 1 && tier.maxAmount() != null) {
        throw new BusinessException(
            "INVALID_TIER_RANGE", "The last approval tier must have an unbounded maxAmount (null)");
      }

      if (tier.steps() == null || tier.steps().isEmpty()) {
        throw new BusinessException("INVALID_TIER_STEPS", "Tier " + tier.tierName() + " must have at least one step");
      }

      // Check step continuity 1, 2, 3...
      List<CreateApprovalStepTemplateRequest> sortedSteps = tier.steps().stream()
          .sorted(Comparator.comparing(CreateApprovalStepTemplateRequest::stepOrder))
          .toList();

      for (int s = 0; s < sortedSteps.size(); s++) {
        CreateApprovalStepTemplateRequest step = sortedSteps.get(s);
        if (step.stepOrder() != s + 1) {
          throw new BusinessException("INVALID_STEP_ORDER", "Step order in tier " + tier.tierName() + " must be strictly 1, 2, 3...");
        }
        if (step.minApprovals() < 1) {
          throw new BusinessException("INVALID_MIN_APPROVALS", "minApprovals must be at least 1");
        }
        if (step.deadlineHours() != null && step.deadlineHours() < 1) {
          throw new BusinessException("INVALID_STEP_DEADLINE", "deadlineHours must be at least 1 when provided");
        }
        String authMethod = step.authMethod() == null
            ? "STANDARD"
            : step.authMethod().trim().toUpperCase();
        if (!List.of("STANDARD", "TOTP_STEPUP", "DIGITAL_SIGNATURE_CA").contains(authMethod)) {
          throw new BusinessException(
              "INVALID_AUTH_METHOD", "Unsupported approval authentication method: " + authMethod);
        }
        int minApprovals = step.minApprovals();
        long qualifiedCount = membershipRepository.findByCorporateIdAndRole(corporateId, step.requiredRole().trim().toUpperCase()).size();
        if (qualifiedCount < minApprovals) {
          throw new BusinessException("INSUFFICIENT_QUALIFIED_MEMBERS",
              "Tier '" + tier.tierName() + "' Step '" + step.stepName() + "' requires " + minApprovals + " member(s) with role " + step.requiredRole() + ", but only " + qualifiedCount + " active member(s) found in this corporation.");
        }
      }
    }
  }

  @Transactional
  public ApprovalPolicyResponse activatePolicy(UUID corporateId, UUID userId, UUID policyId) {
    corporationService.validateAdminOrRole(corporateId, userId, "CORPORATE_ADMIN");
    corporationRepository.findByIdForUpdate(corporateId).orElseThrow(() ->
        new BusinessException("CORPORATION_NOT_FOUND", "Corporation not found"));

    ApprovalPolicyEntity policy = policyRepository.findById(policyId).orElseThrow(() ->
        new BusinessException("POLICY_NOT_FOUND", "Approval policy not found"));

    if (!policy.getCorporateId().equals(corporateId)) {
      throw new BusinessException("FORBIDDEN", "Policy does not belong to this corporation");
    }

    // Retire existing active policy atomically
    policyRepository.findByCorporateIdAndStatus(corporateId, "ACTIVE").ifPresent(active -> {
      active.setStatus("RETIRED");
      active.setEffectiveTo(Instant.now());
      active.setUpdatedAt(Instant.now());
      policyRepository.save(active);
      log.info("[POLICY-RETIRE] Retired active policy [{}] v{} for corporation [{}]",
          active.getId(), active.getVersionNumber(), corporateId);
    });

    policy.setStatus("ACTIVE");
    policy.setEffectiveFrom(Instant.now());
    policy.setUpdatedAt(Instant.now());
    ApprovalPolicyEntity saved = policyRepository.save(policy);

    auditService.log(corporateId, userId, "ACTIVATE_APPROVAL_POLICY", "APPROVAL_POLICY", saved.getId().toString(), "Version=" + saved.getVersionNumber());
    return toResponse(saved);
  }

  @Transactional
  public ApprovalPolicyResponse retirePolicy(UUID corporateId, UUID userId, UUID policyId) {
    corporationService.validateAdminOrRole(corporateId, userId, "CORPORATE_ADMIN");

    ApprovalPolicyEntity policy = policyRepository.findById(policyId).orElseThrow(() ->
        new BusinessException("POLICY_NOT_FOUND", "Approval policy not found"));

    if (!policy.getCorporateId().equals(corporateId)) {
      throw new BusinessException("FORBIDDEN", "Policy does not belong to this corporation");
    }

    policy.setStatus("RETIRED");
    policy.setEffectiveTo(Instant.now());
    policy.setUpdatedAt(Instant.now());
    ApprovalPolicyEntity saved = policyRepository.save(policy);

    auditService.log(corporateId, userId, "RETIRE_APPROVAL_POLICY", "APPROVAL_POLICY", saved.getId().toString(), "Version=" + saved.getVersionNumber());
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public ApprovalPolicyResponse getPolicy(UUID corporateId, UUID userId, UUID policyId) {
    corporationService.validateMembership(corporateId, userId);
    ApprovalPolicyEntity policy = policyRepository.findById(policyId).orElseThrow(() ->
        new BusinessException("POLICY_NOT_FOUND", "Approval policy not found"));
    if (!policy.getCorporateId().equals(corporateId)) {
      throw new BusinessException("FORBIDDEN", "Policy does not belong to this corporation");
    }
    return toResponse(policy);
  }

  @Transactional(readOnly = true)
  public List<ApprovalPolicyResponse> listPolicies(UUID corporateId, UUID userId) {
    corporationService.validateMembership(corporateId, userId);
    return policyRepository.findByCorporateIdOrderByVersionNumberDesc(corporateId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ApprovalPolicyResponse getActivePolicy(UUID corporateId, UUID userId) {
    corporationService.validateMembership(corporateId, userId);
    ApprovalPolicyEntity policy = policyRepository.findByCorporateIdAndStatus(corporateId, "ACTIVE")
        .orElseThrow(() -> new BusinessException("ACTIVE_POLICY_NOT_FOUND", "No active approval policy found for this corporation"));
    return toResponse(policy);
  }

  @Transactional(readOnly = true)
  public SimulateApprovalPlanResponse simulateApprovalPlan(UUID corporateId, UUID userId, BigDecimal totalAmount, String currency) {
    corporationService.validateMembership(corporateId, userId);

    ApprovalPolicyEntity activePolicy = policyRepository.findByCorporateIdAndStatus(corporateId, "ACTIVE")
        .orElseThrow(() -> new BusinessException("ACTIVE_POLICY_NOT_FOUND", "No active approval policy found"));
    requirePolicyCurrency(activePolicy, currency);

    ApprovalTierEntity matchedTier = activePolicy.getTiers().stream()
        .filter(t -> t.matchesAmount(totalAmount))
        .findFirst()
        .orElseThrow(() -> new BusinessException("NO_MATCHING_TIER", "No approval tier matches the amount " + totalAmount));

    List<SimulatedStep> steps = new ArrayList<>();
    for (ApprovalStepTemplateEntity step : matchedTier.getSteps()) {
      List<UUID> eligibleUsers = membershipRepository.findByCorporateIdAndRole(corporateId, step.getRequiredRole())
          .stream()
          .map(CorporateMembershipEntity::getUserId)
          .toList();

      steps.add(new SimulatedStep(
          step.getStepOrder(),
          step.getStepName(),
          step.getRequiredRole(),
          step.getMinApprovals(),
          step.getAuthMethod(),
          eligibleUsers));
    }

    return new SimulateApprovalPlanResponse(
        activePolicy.getId(),
        activePolicy.getVersionNumber(),
        activePolicy.getPolicyName(),
        matchedTier.getTierName(),
        matchedTier.getMinAmount(),
        matchedTier.getMaxAmount(),
        steps);
  }

  @Transactional(readOnly = true)
  public ApprovalTierEntity resolveActiveTier(UUID corporateId, BigDecimal totalAmount, String currency) {
    ApprovalPolicyEntity activePolicy = policyRepository.findByCorporateIdAndStatus(corporateId, "ACTIVE")
        .orElseThrow(() -> new BusinessException("ACTIVE_POLICY_NOT_FOUND", "No active approval policy configured for corporation"));
    requirePolicyCurrency(activePolicy, currency);

    return activePolicy.getTiers().stream()
        .filter(t -> t.matchesAmount(totalAmount))
        .findFirst()
        .orElseThrow(() -> new BusinessException("NO_MATCHING_TIER", "Amount " + totalAmount + " does not match any approval tier"));
  }

  private void requirePolicyCurrency(ApprovalPolicyEntity policy, String currency) {
    String requested = currency == null || currency.isBlank() ? "VND" : currency.trim().toUpperCase();
    if (!policy.getCurrency().equalsIgnoreCase(requested)) {
      throw new BusinessException("POLICY_CURRENCY_MISMATCH",
          "Active approval policy is configured for " + policy.getCurrency() + ", not " + requested);
    }
  }

  public ApprovalPolicyResponse toResponse(ApprovalPolicyEntity p) {
    List<TierResponse> tiers = p.getTiers() != null
        ? p.getTiers().stream().map(this::toTierResponse).toList()
        : List.of();

    return new ApprovalPolicyResponse(
        p.getId(), p.getCorporateId(), p.getPolicyName(), p.getVersionNumber(), p.getStatus(),
        p.getCurrency(), p.isAllowSelfApproval(), p.isRequireRoleSeparation(), p.getEffectiveFrom(),
        p.getEffectiveTo(), p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt(), tiers);
  }

  private TierResponse toTierResponse(ApprovalTierEntity t) {
    List<StepTemplateResponse> steps = t.getSteps() != null
        ? t.getSteps().stream().map(this::toStepResponse).toList()
        : List.of();

    return new TierResponse(
        t.getId(), t.getTierName(), t.getMinAmount(), t.getMaxAmount(), t.getPriorityOrder(), steps);
  }

  private StepTemplateResponse toStepResponse(ApprovalStepTemplateEntity s) {
    return new StepTemplateResponse(
        s.getId(), s.getStepOrder(), s.getStepName(), s.getRequiredRole(),
        s.getMinApprovals(), s.getAuthMethod(), s.getDeadlineHours());
  }
}
