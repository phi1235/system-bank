package com.banksystem.corporate.application.approval;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalActionRequest;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalActionResponse;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalInstanceDetailResponse;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalTaskResponse;
import com.banksystem.corporate.application.audit.CorporateAuditService;
import com.banksystem.corporate.application.corporation.CorporationService;
import com.banksystem.corporate.domain.approval.ApprovalActionEntity;
import com.banksystem.corporate.domain.approval.ApprovalActionRepository;
import com.banksystem.corporate.domain.approval.ApprovalInstanceEntity;
import com.banksystem.corporate.domain.approval.ApprovalInstanceRepository;
import com.banksystem.corporate.domain.approval.ApprovalPolicyEntity;
import com.banksystem.corporate.domain.approval.ApprovalPolicyRepository;
import com.banksystem.corporate.domain.approval.ApprovalStepTemplateEntity;
import com.banksystem.corporate.domain.approval.ApprovalTaskEntity;
import com.banksystem.corporate.domain.approval.ApprovalTaskRepository;
import com.banksystem.corporate.domain.approval.ApprovalTierEntity;
import com.banksystem.corporate.domain.approval.SigningChallengeEntity;
import com.banksystem.corporate.domain.corporation.CorporateMembershipEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalWorkflowService {

  private static final Logger log = LoggerFactory.getLogger(ApprovalWorkflowService.class);

  private final ApprovalInstanceRepository instanceRepository;
  private final ApprovalTaskRepository taskRepository;
  private final ApprovalActionRepository actionRepository;
  private final ApprovalPolicyRepository policyRepository;
  private final PayoutBatchRepository batchRepository;
  private final CorporationService corporationService;
  private final SigningChallengeService signingChallengeService;
  private final CorporateAuditService auditService;

  public ApprovalWorkflowService(
      ApprovalInstanceRepository instanceRepository,
      ApprovalTaskRepository taskRepository,
      ApprovalActionRepository actionRepository,
      ApprovalPolicyRepository policyRepository,
      PayoutBatchRepository batchRepository,
      CorporationService corporationService,
      SigningChallengeService signingChallengeService,
      CorporateAuditService auditService) {
    this.instanceRepository = instanceRepository;
    this.taskRepository = taskRepository;
    this.actionRepository = actionRepository;
    this.policyRepository = policyRepository;
    this.batchRepository = batchRepository;
    this.corporationService = corporationService;
    this.signingChallengeService = signingChallengeService;
    this.auditService = auditService;
  }

  @Transactional
  public ApprovalInstanceEntity instantiateWorkflow(
      PayoutBatchEntity batch,
      ApprovalTierEntity tier,
      int policyVersion) {
    ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
    instance.setId(UUID.randomUUID());
    instance.setBatch(batch);
    instance.setBatchId(batch.getId());
    instance.setTierId(tier.getId());
    instance.setPolicyVersion(policyVersion);
    instance.setTotalSteps(tier.getSteps().size());
    instance.setCurrentStep(1);
    instance.setStatus("IN_PROGRESS");
    instance.setCreatedAt(Instant.now());
    instance.setUpdatedAt(Instant.now());

    List<ApprovalTaskEntity> tasks = new ArrayList<>();
    for (ApprovalStepTemplateEntity template : tier.getSteps()) {
      ApprovalTaskEntity task = new ApprovalTaskEntity();
      task.setId(UUID.randomUUID());
      task.setInstance(instance);
      task.setBatch(batch);
      task.setBatchId(batch.getId());
      task.setStepOrder(template.getStepOrder());
      task.setStepName(template.getStepName());
      task.setRequiredRole(template.getRequiredRole());
      task.setMinApprovals(template.getMinApprovals());
      task.setCurrentApprovals(0);
      task.setAuthMethod(template.getAuthMethod());
      task.setStatus(template.getStepOrder() == 1 ? "ACTIVE" : "PENDING");
      if (template.getDeadlineHours() != null && template.getDeadlineHours() > 0) {
        task.setDeadline(Instant.now().plusSeconds(template.getDeadlineHours() * 3600L));
      }
      task.setCreatedAt(Instant.now());
      task.setUpdatedAt(Instant.now());
      tasks.add(task);
    }
    instance.setTasks(tasks);

    ApprovalInstanceEntity saved = instanceRepository.save(instance);
    log.info("[WORKFLOW-INIT] Instantiated approval workflow [{}] for Batch [{}] with {} steps",
        saved.getId(), batch.getId(), tasks.size());
    return saved;
  }

  @Transactional
  public ApprovalTaskResponse approveTask(
      UUID taskId,
      UUID actorUserId,
      ApprovalActionRequest req,
      String ip,
      String userAgent) {
    ApprovalTaskEntity task = taskRepository.findByIdForUpdate(taskId).orElseThrow(() ->
        new BusinessException("TASK_NOT_FOUND", "Approval task not found"));

    if (!"ACTIVE".equals(task.getStatus())) {
      throw new BusinessException("TASK_NOT_ACTIVE", "This approval task is not currently active");
    }

    PayoutBatchEntity batch = task.getBatch();
    CorporateMembershipEntity membership = corporationService.validateMembership(batch.getCorporateId(), actorUserId);

    if (!membership.hasRole(task.getRequiredRole())) {
      throw new BusinessException("FORBIDDEN_INSUFFICIENT_ROLE", "You do not have the required role: " + task.getRequiredRole());
    }

    if (task.getDeadline() != null && task.getDeadline().isBefore(Instant.now())) {
      throw new BusinessException("TASK_DEADLINE_EXPIRED", "Approval task deadline has expired");
    }

    ApprovalPolicyEntity policy = policyRepository.findByCorporateIdAndVersionNumber(batch.getCorporateId(), batch.getPolicyVersion())
        .orElse(null);

    // Rule: Allow self-approval check
    if (policy != null && !policy.isAllowSelfApproval()) {
      if (actorUserId.equals(batch.getSubmittedBy()) || actorUserId.equals(batch.getCreatedBy())) {
        throw new BusinessException("SELF_APPROVAL_NOT_ALLOWED", "Maker is not allowed to approve their own batch");
      }
    }

    // Rule: Separation of duties check
    if (policy != null && policy.isRequireRoleSeparation()) {
      if (actionRepository.existsByBatchIdAndActorId(batch.getId(), actorUserId)) {
        throw new BusinessException("ROLE_SEPARATION_VIOLATION", "You have already participated in approving a step for this batch");
      }
    }

    // Check duplicate approval on this task
    if (actionRepository.existsByTaskIdAndActorId(taskId, actorUserId)) {
      throw new BusinessException("DUPLICATE_APPROVAL", "You have already approved this task");
    }

    // Verify step-up / CA signature if required
    UUID challengeId = null;
    if ("TOTP_STEPUP".equalsIgnoreCase(task.getAuthMethod()) || "DIGITAL_SIGNATURE_CA".equalsIgnoreCase(task.getAuthMethod())) {
      SigningChallengeEntity challenge = signingChallengeService.verifyChallenge(
          req.challengeNonce(), taskId, actorUserId, req.authCode(), req.signatureReference());
      challengeId = challenge.getId();
    }

    // Record Action
    ApprovalActionEntity action = new ApprovalActionEntity();
    action.setId(UUID.randomUUID());
    action.setTask(task);
    action.setBatch(batch);
    action.setActorId(actorUserId);
    action.setActorRole(task.getRequiredRole());
    action.setAction("APPROVE");
    action.setComments(req.comments());
    action.setChallengeId(challengeId);
    action.setSignatureReference(req.signatureReference());
    action.setActionTimestamp(Instant.now());
    action.setIpAddress(ip);
    action.setUserAgent(userAgent);
    actionRepository.save(action);

    task.setCurrentApprovals(task.getCurrentApprovals() + 1);
    task.setUpdatedAt(Instant.now());

    if (task.isSatisfied()) {
      task.setStatus("APPROVED");
      taskRepository.save(task);

      ApprovalInstanceEntity instance = task.getInstance();
      int nextStepOrder = task.getStepOrder() + 1;
      var nextTaskOpt = taskRepository.findByInstanceIdAndStepOrder(instance.getId(), nextStepOrder);

      if (nextTaskOpt.isPresent()) {
        ApprovalTaskEntity nextTask = nextTaskOpt.get();
        nextTask.setStatus("ACTIVE");
        nextTask.setUpdatedAt(Instant.now());
        taskRepository.save(nextTask);

        instance.setCurrentStep(nextStepOrder);
        instance.setUpdatedAt(Instant.now());
        instanceRepository.save(instance);
        log.info("[WORKFLOW-STEP] Step {} approved. Activated next Step {} for batch [{}]",
            task.getStepOrder(), nextStepOrder, batch.getId());
      } else {
        // Last step completed!
        instance.setStatus("APPROVED");
        instance.setUpdatedAt(Instant.now());
        instanceRepository.save(instance);

        batch.setStatus("APPROVED");
        batch.setApprovedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        batchRepository.save(batch);
        log.info("[WORKFLOW-COMPLETE] All approval steps satisfied! Batch [{}] is now APPROVED", batch.getId());
      }
    } else {
      taskRepository.save(task);
    }

    auditService.logWithIp(batch.getCorporateId(), actorUserId, "APPROVE_TASK", "APPROVAL_TASK", taskId.toString(),
        "Step=" + task.getStepName() + ",Role=" + task.getRequiredRole() + ",Approvals=" + task.getCurrentApprovals() + "/" + task.getMinApprovals(), ip);

    return toTaskResponse(task);
  }

  @Transactional
  public ApprovalTaskResponse rejectTask(
      UUID taskId,
      UUID actorUserId,
      String reason,
      String ip,
      String userAgent) {
    ApprovalTaskEntity task = taskRepository.findByIdForUpdate(taskId).orElseThrow(() ->
        new BusinessException("TASK_NOT_FOUND", "Approval task not found"));

    if (!"ACTIVE".equals(task.getStatus())) {
      throw new BusinessException("TASK_NOT_ACTIVE", "This approval task is not active");
    }

    PayoutBatchEntity batch = task.getBatch();
    CorporateMembershipEntity membership = corporationService.validateMembership(batch.getCorporateId(), actorUserId);
    if (!membership.hasRole(task.getRequiredRole())) {
      throw new BusinessException("FORBIDDEN_INSUFFICIENT_ROLE", "You do not have the required role to reject this step: " + task.getRequiredRole());
    }

    ApprovalActionEntity action = new ApprovalActionEntity();
    action.setId(UUID.randomUUID());
    action.setTask(task);
    action.setBatch(batch);
    action.setActorId(actorUserId);
    action.setActorRole(task.getRequiredRole());
    action.setAction("REJECT");
    action.setComments(reason);
    action.setActionTimestamp(Instant.now());
    action.setIpAddress(ip);
    action.setUserAgent(userAgent);
    actionRepository.save(action);

    task.setStatus("REJECTED");
    task.setUpdatedAt(Instant.now());
    taskRepository.save(task);

    ApprovalInstanceEntity instance = task.getInstance();
    instance.setStatus("REJECTED");
    instance.setUpdatedAt(Instant.now());
    instanceRepository.save(instance);

    batch.setStatus("REJECTED");
    batch.setUpdatedAt(Instant.now());
    batchRepository.save(batch);

    auditService.logWithIp(batch.getCorporateId(), actorUserId, "REJECT_TASK", "APPROVAL_TASK", taskId.toString(),
        "Step=" + task.getStepName() + ",Reason=" + reason, ip);

    return toTaskResponse(task);
  }

  @Transactional
  public ApprovalTaskResponse returnTask(
      UUID taskId,
      UUID actorUserId,
      String reason,
      String ip,
      String userAgent) {
    ApprovalTaskEntity task = taskRepository.findByIdForUpdate(taskId).orElseThrow(() ->
        new BusinessException("TASK_NOT_FOUND", "Approval task not found"));

    if (!"ACTIVE".equals(task.getStatus())) {
      throw new BusinessException("TASK_NOT_ACTIVE", "This approval task is not active");
    }

    PayoutBatchEntity batch = task.getBatch();
    CorporateMembershipEntity membership = corporationService.validateMembership(batch.getCorporateId(), actorUserId);
    if (!membership.hasRole(task.getRequiredRole())) {
      throw new BusinessException("FORBIDDEN_INSUFFICIENT_ROLE", "You do not have the required role to return this step: " + task.getRequiredRole());
    }

    ApprovalActionEntity action = new ApprovalActionEntity();
    action.setId(UUID.randomUUID());
    action.setTask(task);
    action.setBatch(batch);
    action.setActorId(actorUserId);
    action.setActorRole(task.getRequiredRole());
    action.setAction("RETURN");
    action.setComments(reason);
    action.setActionTimestamp(Instant.now());
    action.setIpAddress(ip);
    action.setUserAgent(userAgent);
    actionRepository.save(action);

    task.setStatus("RETURNED");
    task.setUpdatedAt(Instant.now());
    taskRepository.save(task);

    ApprovalInstanceEntity instance = task.getInstance();
    instance.setStatus("RETURNED");
    instance.setUpdatedAt(Instant.now());
    instanceRepository.save(instance);

    batch.setStatus("RETURNED");
    batch.setUpdatedAt(Instant.now());
    batchRepository.save(batch);

    auditService.logWithIp(batch.getCorporateId(), actorUserId, "RETURN_TASK", "APPROVAL_TASK", taskId.toString(),
        "Step=" + task.getStepName() + ",Reason=" + reason, ip);

    return toTaskResponse(task);
  }

  @Transactional(readOnly = true)
  public ApprovalInstanceDetailResponse getInstanceDetail(UUID batchId, UUID userId) {
    PayoutBatchEntity batch = batchRepository.findById(batchId).orElseThrow(() ->
        new BusinessException("BATCH_NOT_FOUND", "Payout batch not found"));
    corporationService.validateMembership(batch.getCorporateId(), userId);

    ApprovalInstanceEntity instance = instanceRepository.findByBatchId(batchId).orElseThrow(() ->
        new BusinessException("APPROVAL_INSTANCE_NOT_FOUND", "No approval workflow instance found for this batch"));

    List<ApprovalTaskResponse> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getId()).stream()
        .map(this::toTaskResponse)
        .toList();

    List<ApprovalActionResponse> actions = actionRepository.findByBatchIdOrderByActionTimestampAsc(batchId).stream()
        .map(this::toActionResponse)
        .toList();

    return new ApprovalInstanceDetailResponse(
        instance.getId(), instance.getBatchId(), instance.getPolicyVersion(), instance.getTotalSteps(),
        instance.getCurrentStep(), instance.getStatus(), tasks, actions);
  }

  @Transactional(readOnly = true)
  public List<ApprovalTaskResponse> getInbox(UUID userId) {
    return taskRepository.findInboxForUser(userId).stream()
        .map(this::toTaskResponse)
        .toList();
  }

  public String calculateCanonicalPayloadHash(
      UUID batchId,
      UUID corporateId,
      UUID sourceAccountId,
      BigDecimal totalAmount,
      String fileSha256,
      String currency,
      int policyVersion) {
    String raw = batchId + "|" + corporateId + "|" + sourceAccountId + "|"
        + totalAmount.toPlainString() + "|" + fileSha256 + "|" + currency.toUpperCase() + "|" + policyVersion;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  public ApprovalTaskResponse toTaskResponse(ApprovalTaskEntity t) {
    PayoutBatchEntity b = t.getBatch();
    return new ApprovalTaskResponse(
        t.getId(), t.getInstance().getId(), t.getBatchId(), b != null ? b.getBatchName() : "",
        b != null ? b.getCorporateId() : null, t.getStepOrder(), t.getStepName(), t.getRequiredRole(),
        t.getMinApprovals(), t.getCurrentApprovals(), t.getAuthMethod(), t.getStatus(), t.getDeadline(),
        b != null ? b.getTotalAmount() : BigDecimal.ZERO, b != null ? b.getTotalItems() : 0,
        b != null ? b.getCurrency() : "VND", t.getCreatedAt());
  }

  private ApprovalActionResponse toActionResponse(ApprovalActionEntity a) {
    return new ApprovalActionResponse(
        a.getId(), a.getTask().getId(), a.getBatch().getId(), a.getActorId(),
        a.getActorRole(), a.getAction(), a.getComments(), a.getActionTimestamp());
  }
}
