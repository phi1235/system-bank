package com.banksystem.corporate.application.payout;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchProgressResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchSummaryResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchValidationSummaryResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.CreateBatchRequest;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.PayoutItemResponse;
import com.banksystem.corporate.application.approval.ApprovalMatrixService;
import com.banksystem.corporate.application.approval.ApprovalWorkflowService;
import com.banksystem.corporate.application.audit.CorporateAuditService;
import com.banksystem.corporate.application.corporation.CorporationService;
import com.banksystem.corporate.domain.approval.ApprovalInstanceEntity;
import com.banksystem.corporate.domain.approval.ApprovalInstanceRepository;
import com.banksystem.corporate.domain.approval.ApprovalTaskEntity;
import com.banksystem.corporate.domain.approval.ApprovalTaskRepository;
import com.banksystem.corporate.domain.approval.ApprovalTierEntity;
import com.banksystem.corporate.domain.corporation.CorporateAccountRepository;
import com.banksystem.corporate.domain.corporation.CorporateMembershipEntity;
import com.banksystem.corporate.domain.outbox.CorporateOutboxEventEntity;
import com.banksystem.corporate.domain.outbox.CorporateOutboxEventRepository;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutBatchService {

  private static final Logger log = LoggerFactory.getLogger(PayoutBatchService.class);

  private final PayoutBatchRepository batchRepository;
  private final PayoutItemRepository itemRepository;
  private final CorporateAccountRepository corporateAccountRepository;
  private final CorporationService corporationService;
  private final ApprovalMatrixService approvalMatrixService;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final CorporateAuditService auditService;
  private final CorporateOutboxEventRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final ApprovalInstanceRepository approvalInstanceRepository;
  private final ApprovalTaskRepository approvalTaskRepository;

  public PayoutBatchService(
      PayoutBatchRepository batchRepository,
      PayoutItemRepository itemRepository,
      CorporateAccountRepository corporateAccountRepository,
      CorporationService corporationService,
      ApprovalMatrixService approvalMatrixService,
      ApprovalWorkflowService approvalWorkflowService,
      CorporateAuditService auditService,
      CorporateOutboxEventRepository outboxRepository,
      ObjectMapper objectMapper,
      ApprovalInstanceRepository approvalInstanceRepository,
      ApprovalTaskRepository approvalTaskRepository) {
    this.batchRepository = batchRepository;
    this.itemRepository = itemRepository;
    this.corporateAccountRepository = corporateAccountRepository;
    this.corporationService = corporationService;
    this.approvalMatrixService = approvalMatrixService;
    this.approvalWorkflowService = approvalWorkflowService;
    this.auditService = auditService;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.approvalInstanceRepository = approvalInstanceRepository;
    this.approvalTaskRepository = approvalTaskRepository;
  }

  @Transactional
  public BatchSummaryResponse createBatch(UUID corporateId, UUID userId, CreateBatchRequest req) {
    CorporateMembershipEntity m = corporationService.validateMembership(corporateId, userId);
    if (!m.hasRole("MAKER")) {
      throw new BusinessException("FORBIDDEN", "Only MAKER can create payout batches");
    }

    var linkedAccount = corporateAccountRepository.findByCorporateIdAndAccountId(corporateId, req.sourceAccountId())
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_LINKED", "Source account is not linked to this corporation"));
    if (!"ACTIVE".equals(linkedAccount.getStatus())) {
      throw new BusinessException("CORPORATE_ACCOUNT_INACTIVE", "Source corporate account is not active");
    }

    PayoutBatchEntity batch = new PayoutBatchEntity();
    batch.setId(UUID.randomUUID());
    batch.setCorporateId(corporateId);
    batch.setSourceAccountId(req.sourceAccountId());
    batch.setSourceAccountNumber(linkedAccount.getAccountNumber());
    batch.setBatchName(req.batchName().trim());
    batch.setCurrency(linkedAccount.getCurrency());
    batch.setFileSha256("PENDING_" + UUID.randomUUID());
    batch.setStatus("DRAFT");
    batch.setCreatedBy(userId);
    batch.setCreatedAt(Instant.now());
    batch.setUpdatedAt(Instant.now());

    PayoutBatchEntity saved = batchRepository.save(batch);
    auditService.log(corporateId, userId, "CREATE_BATCH", "PAYOUT_BATCH", saved.getId().toString(), "Name=" + saved.getBatchName());
    return toSummaryResponse(saved);
  }

  @Transactional
  public BatchSummaryResponse submitBatch(UUID corporateId, UUID batchId, UUID userId) {
    PayoutBatchEntity batch = requireTenantBatch(corporateId, batchId, userId);

    CorporateMembershipEntity m = corporationService.validateMembership(corporateId, userId);
    if (!m.hasRole("MAKER")) {
      throw new BusinessException("FORBIDDEN", "Only MAKER can submit payout batches");
    }

    if (!"READY_FOR_SUBMISSION".equals(batch.getStatus())) {
      throw new BusinessException("INVALID_BATCH_STATUS", "Batch must be in READY_FOR_SUBMISSION status to submit (current: " + batch.getStatus() + ")");
    }
    if (batch.getTotalItems() == 0 || batch.getValidItems() == 0 || batch.getInvalidItems() > 0) {
      throw new BusinessException("BATCH_HAS_ERRORS", "Batch contains validation errors or no valid items");
    }

    var linkedAccount = corporateAccountRepository.findLinkedAccountForUpdate(
        corporateId, batch.getSourceAccountId()).orElseThrow(() ->
            new BusinessException("ACCOUNT_NOT_LINKED", "Source account is not linked to this corporation"));
    if (linkedAccount.getDailyPayoutLimit() != null) {
      Instant dayStart = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
          .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
      BigDecimal committed = batchRepository.sumCommittedPayoutForDay(
          corporateId, batch.getSourceAccountId(), batchId, dayStart);
      BigDecimal requested = batch.getTotalAmount().add(batch.getTotalFee());
      if (committed.add(requested).compareTo(linkedAccount.getDailyPayoutLimit()) > 0) {
        throw new BusinessException("DAILY_PAYOUT_LIMIT_EXCEEDED",
            "Daily corporate payout limit exceeded for source account");
      }
    }

    ApprovalTierEntity tier = approvalMatrixService.resolveActiveTier(
        batch.getCorporateId(), batch.getTotalAmount(), batch.getCurrency());
    var activePolicy = tier.getPolicy();

    String snapshotJson;
    try {
      snapshotJson = objectMapper.writeValueAsString(approvalMatrixService.toResponse(activePolicy));
    } catch (Exception e) {
      snapshotJson = "{}";
    }

    String canonicalHash = approvalWorkflowService.calculateCanonicalPayloadHash(
        batch.getId(),
        batch.getCorporateId(),
        batch.getSourceAccountId(),
        batch.getTotalAmount(),
        batch.getFileSha256(),
        batch.getCurrency(),
        activePolicy.getVersionNumber());

    batch.setPolicyId(activePolicy.getId());
    batch.setPolicyVersion(activePolicy.getVersionNumber());
    batch.setPolicySnapshot(snapshotJson);
    batch.setCanonicalPayloadHash(canonicalHash);
    batch.setStatus("PENDING_APPROVAL");
    batch.setSubmittedBy(userId);
    batch.setSubmittedAt(Instant.now());
    batch.setUpdatedAt(Instant.now());

    approvalWorkflowService.instantiateWorkflow(batch, tier, activePolicy.getVersionNumber());

    PayoutBatchEntity saved = batchRepository.save(batch);

    outboxRepository.save(CorporateOutboxEventEntity.of(
        "PAYOUT_BATCH", saved.getId(), "PAYOUT_BATCH_SUBMITTED",
        "{\"batchId\":\"" + saved.getId() + "\",\"corporateId\":\"" + saved.getCorporateId() + "\",\"totalAmount\":" + saved.getTotalAmount() + "}"));

    auditService.log(batch.getCorporateId(), userId, "SUBMIT_BATCH", "PAYOUT_BATCH", saved.getId().toString(),
        "Amount=" + saved.getTotalAmount() + ",PolicyVersion=" + activePolicy.getVersionNumber() + ",Tier=" + tier.getTierName());

    return toSummaryResponse(saved);
  }

  @Transactional
  public BatchSummaryResponse cancelBatch(UUID corporateId, UUID batchId, UUID userId, String reason) {
    PayoutBatchEntity batch = requireTenantBatch(corporateId, batchId, userId);
    requireMaker(corporateId, userId);

    if (!"PENDING_APPROVAL".equals(batch.getStatus()) && !"READY_FOR_SUBMISSION".equals(batch.getStatus()) && !"RETURNED".equals(batch.getStatus()) && !"DRAFT".equals(batch.getStatus())) {
      throw new BusinessException("CANNOT_CANCEL_BATCH", "Batch in state " + batch.getStatus() + " cannot be cancelled");
    }

    batch.setStatus("CANCELLED");
    batch.setUpdatedAt(Instant.now());
    PayoutBatchEntity saved = batchRepository.save(batch);

    approvalInstanceRepository.findByBatchId(batchId).ifPresent(instance -> {
      if ("IN_PROGRESS".equals(instance.getStatus())) {
        instance.setStatus("CANCELLED");
        instance.setUpdatedAt(Instant.now());
        approvalInstanceRepository.save(instance);

        List<ApprovalTaskEntity> tasks = approvalTaskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getId());
        for (ApprovalTaskEntity task : tasks) {
          if ("PENDING".equals(task.getStatus()) || "ACTIVE".equals(task.getStatus())) {
            task.setStatus("REJECTED"); // Cancelled is not in enum, using REJECTED
            task.setUpdatedAt(Instant.now());
            approvalTaskRepository.save(task);
          }
        }
      }
    });

    auditService.log(batch.getCorporateId(), userId, "CANCEL_BATCH", "PAYOUT_BATCH", saved.getId().toString(), "Reason=" + reason);
    return toSummaryResponse(saved);
  }

  @Transactional
  public BatchSummaryResponse retryFailedItems(UUID corporateId, UUID batchId, UUID userId) {
    PayoutBatchEntity batch = requireTenantBatch(corporateId, batchId, userId);
    requireMaker(corporateId, userId);

    if (!"PARTIALLY_COMPLETED".equals(batch.getStatus()) && !"FAILED".equals(batch.getStatus()) && !"PROCESSING".equals(batch.getStatus())) {
      throw new BusinessException("CANNOT_RETRY", "Batch is not eligible for retry in state: " + batch.getStatus());
    }

    List<PayoutItemEntity> items = itemRepository.findByBatchIdOrderByRowNumberAsc(batchId);
    int retried = 0;
    for (PayoutItemEntity item : items) {
      if ("FAILED_FINAL".equals(item.getStatus()) || "RETRY_WAIT".equals(item.getStatus()) || "UNKNOWN".equals(item.getStatus())) {
        item.setStatus("QUEUED");
        item.setExecutionVersion(item.getExecutionVersion() + 1);
        item.setIdempotencyKey("CORP:" + batchId + ":" + item.getId() + ":v" + item.getExecutionVersion());
        item.setRetryCount(0);
        item.setNextRetryAt(Instant.now());
        item.setLeaseUntil(null);
        item.setClaimedBy(null);
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        retried++;
      }
    }

    if (retried > 0) {
      batch.setStatus("PROCESSING");
      batch.setUpdatedAt(Instant.now());
      batchRepository.save(batch);
      log.info("[BATCH-RETRY] Retried {} items for batch [{}]", retried, batchId);
    }

    auditService.log(batch.getCorporateId(), userId, "RETRY_FAILED_ITEMS", "PAYOUT_BATCH", batchId.toString(), "RetriedItems=" + retried);
    return toSummaryResponse(batch);
  }

  @Transactional(readOnly = true)
  public BatchSummaryResponse getBatch(UUID corporateId, UUID batchId, UUID userId) {
    PayoutBatchEntity batch = requireTenantBatch(corporateId, batchId, userId);
    return toSummaryResponse(batch);
  }

  @Transactional(readOnly = true)
  public void requireMakerBatch(UUID corporateId, UUID batchId, UUID userId) {
    requireTenantBatch(corporateId, batchId, userId);
    requireMaker(corporateId, userId);
  }

  @Transactional(readOnly = true)
  public Page<BatchSummaryResponse> listBatches(UUID corporateId, UUID userId, Pageable pageable) {
    corporationService.validateMembership(corporateId, userId);
    return batchRepository.findByCorporateIdOrderByCreatedAtDesc(corporateId, pageable)
        .map(this::toSummaryResponse);
  }

  @Transactional(readOnly = true)
  public Page<PayoutItemResponse> listBatchItems(UUID corporateId, UUID batchId, UUID userId, Pageable pageable) {
    requireTenantBatch(corporateId, batchId, userId);
    return itemRepository.findByBatchIdOrderByRowNumberAsc(batchId, pageable)
        .map(this::toItemResponse);
  }

  @Transactional(readOnly = true)
  public BatchValidationSummaryResponse getValidationSummary(UUID corporateId, UUID batchId, UUID userId) {
    PayoutBatchEntity batch = requireTenantBatch(corporateId, batchId, userId);

    List<String> blockingErrors = new ArrayList<>();
    if (batch.getInvalidItems() > 0) {
      blockingErrors.add("Có " + batch.getInvalidItems() + " dòng dữ liệu không hợp lệ. Vui lòng tải file báo cáo lỗi để kiểm tra.");
    }
    if (batch.getTotalItems() == 0) {
      blockingErrors.add("Danh sách không có dòng dữ liệu nào.");
    }

    boolean canSubmit = "READY_FOR_SUBMISSION".equals(batch.getStatus()) && blockingErrors.isEmpty();

    return new BatchValidationSummaryResponse(
        batchId, batch.getTotalItems(), batch.getValidItems(), batch.getInvalidItems(),
        batch.getTotalAmount(), canSubmit, blockingErrors);
  }

  @Transactional(readOnly = true)
  public BatchProgressResponse getProgress(UUID corporateId, UUID batchId, UUID userId) {
    PayoutBatchEntity batch = requireTenantBatch(corporateId, batchId, userId);

    int total = batch.getTotalItems();
    int processed = batch.getProcessedItems();
    double percentage = total > 0 ? (processed * 100.0 / total) : 0.0;

    BigDecimal processedAmount = itemRepository.findByBatchIdOrderByRowNumberAsc(batchId).stream()
        .filter(i -> "SUCCESS".equals(i.getStatus()))
        .map(PayoutItemEntity::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new BatchProgressResponse(
        batchId, batch.getStatus(), total, processed, batch.getSuccessfulItems(),
        batch.getFailedItems(), Math.round(percentage * 10.0) / 10.0, processedAmount, batch.getTotalAmount());
  }

  public BatchSummaryResponse toSummaryResponse(PayoutBatchEntity b) {
    return new BatchSummaryResponse(
        b.getId(), b.getCorporateId(), b.getSourceAccountId(), b.getSourceAccountNumber(),
        b.getBatchName(), b.getTotalItems(), b.getValidItems(), b.getInvalidItems(),
        b.getProcessedItems(), b.getSuccessfulItems(), b.getFailedItems(), b.getTotalAmount(),
        b.getTotalFee(), b.getCurrency(), b.getStatus(), b.getFileSha256(), b.getPolicyId(),
        b.getPolicyVersion(), b.getCanonicalPayloadHash(), b.getHoldId(), b.getCreatedBy(),
        b.getSubmittedBy(), b.getSubmittedAt(), b.getApprovedAt(), b.getStartedAt(),
        b.getCompletedAt(), b.getCreatedAt(), b.getUpdatedAt());
  }

  private PayoutBatchEntity requireTenantBatch(UUID corporateId, UUID batchId, UUID userId) {
    corporationService.validateMembership(corporateId, userId);
    return batchRepository.findByCorporateIdAndId(corporateId, batchId).orElseThrow(() ->
        new BusinessException("BATCH_NOT_FOUND", "Payout batch not found"));
  }

  private void requireMaker(UUID corporateId, UUID userId) {
    CorporateMembershipEntity membership = corporationService.validateMembership(corporateId, userId);
    if (!membership.hasRole("MAKER")) {
      throw new BusinessException("FORBIDDEN", "Only MAKER can perform this operation");
    }
  }

  public PayoutItemResponse toItemResponse(PayoutItemEntity i) {
    return new PayoutItemResponse(
        i.getId(), i.getBatchId(), i.getRowNumber(), i.getEmployeeCode(), i.getBeneficiaryName(),
        i.getAccountNumber(), i.getBankCode(), i.getAmount(), i.getFeeAmount(), i.getCurrency(),
        i.getDescription(), i.getEmployeeEmail(), i.getPayrollPeriod(), i.getStatus(),
        i.getValidationError(), i.getTransactionId(), i.getIdempotencyKey(), i.getExecutionVersion(),
        i.getRetryCount(), i.getFailureReason(), i.getReceiptArtifactId(), i.getCreatedAt(), i.getUpdatedAt());
  }
}
