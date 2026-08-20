package com.banksystem.corporate.application.approval;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.CreateChallengeResponse;
import com.banksystem.corporate.application.signature.TransactionSignatureProvider;
import com.banksystem.corporate.application.corporation.CorporationService;
import com.banksystem.corporate.application.signature.TransactionSignatureProvider.SignatureVerificationResult;
import com.banksystem.corporate.domain.approval.ApprovalTaskEntity;
import com.banksystem.corporate.domain.approval.ApprovalTaskRepository;
import com.banksystem.corporate.domain.approval.SigningChallengeEntity;
import com.banksystem.corporate.domain.approval.SigningChallengeRepository;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.infrastructure.config.InternalApiKeyProperties;
import com.banksystem.corporate.infrastructure.feign.AuthClient;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.VerifyTotpReq;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningChallengeService {

  private static final Logger log = LoggerFactory.getLogger(SigningChallengeService.class);
  private static final int MAX_ATTEMPTS = 5;
  private static final long CHALLENGE_TTL_SECONDS = 300; // 5 minutes

  private final SigningChallengeRepository challengeRepository;
  private final ApprovalTaskRepository taskRepository;
  private final AuthClient authClient;
  private final TransactionSignatureProvider signatureProvider;
  private final ChallengeAttemptRecorder attemptRecorder;
  private final InternalApiKeyProperties apiKeyProperties;
  private final CorporationService corporationService;

  public SigningChallengeService(
      SigningChallengeRepository challengeRepository,
      ApprovalTaskRepository taskRepository,
      AuthClient authClient,
      TransactionSignatureProvider signatureProvider,
      ChallengeAttemptRecorder attemptRecorder,
      InternalApiKeyProperties apiKeyProperties,
      CorporationService corporationService) {
    this.challengeRepository = challengeRepository;
    this.taskRepository = taskRepository;
    this.authClient = authClient;
    this.signatureProvider = signatureProvider;
    this.attemptRecorder = attemptRecorder;
    this.apiKeyProperties = apiKeyProperties;
    this.corporationService = corporationService;
  }

  @Transactional
  public CreateChallengeResponse createChallenge(UUID taskId, UUID userId) {
    ApprovalTaskEntity task = taskRepository.findById(taskId).orElseThrow(() ->
        new BusinessException("TASK_NOT_FOUND", "Approval task not found"));

    if (!"ACTIVE".equals(task.getStatus())) {
      throw new BusinessException("TASK_NOT_ACTIVE", "Task is not active for approval");
    }

    PayoutBatchEntity batch = task.getBatch();
    var membership = corporationService.validateMembership(batch.getCorporateId(), userId);
    if (!membership.hasRole(task.getRequiredRole())) {
      throw new BusinessException("FORBIDDEN_INSUFFICIENT_ROLE",
          "You do not have the required role: " + task.getRequiredRole());
    }
    String payloadHash = batch.getCanonicalPayloadHash();
    if (payloadHash == null || payloadHash.isBlank()) {
      throw new BusinessException("INVALID_BATCH_HASH", "Batch payload hash is missing or corrupted");
    }

    String nonce = UUID.randomUUID().toString().replace("-", "");
    Instant expiresAt = Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS);

    SigningChallengeEntity challenge = new SigningChallengeEntity();
    challenge.setId(UUID.randomUUID());
    challenge.setTask(task);
    challenge.setBatch(batch);
    challenge.setUserId(userId);
    challenge.setChallengeType(task.getAuthMethod());
    challenge.setNonce(nonce);
    challenge.setPayloadHash(payloadHash);
    challenge.setExpiresAt(expiresAt);
    challenge.setAttemptCount(0);
    challenge.setVerified(false);
    challenge.setCreatedAt(Instant.now());

    SigningChallengeEntity saved = challengeRepository.save(challenge);
    log.info("[CHALLENGE-CREATED] Created signing challenge [{}] Type=[{}] Task=[{}] User=[{}]",
        saved.getId(), saved.getChallengeType(), taskId, userId);

    return new CreateChallengeResponse(
        saved.getId(), saved.getNonce(), saved.getChallengeType(), saved.getPayloadHash(), saved.getExpiresAt());
  }

  public SigningChallengeEntity verifyChallenge(
      String nonce,
      UUID taskId,
      UUID userId,
      String authCode,
      String signatureReference) {
    if (nonce == null || nonce.isBlank()) {
      throw new BusinessException("CHALLENGE_NONCE_REQUIRED", "Challenge nonce is required");
    }

    SigningChallengeEntity challenge = challengeRepository.findByNonce(nonce.trim()).orElseThrow(() ->
        new BusinessException("CHALLENGE_NOT_FOUND", "Signing challenge not found or invalid"));

    if (!challenge.getTask().getId().equals(taskId)) {
      throw new BusinessException("CHALLENGE_TASK_MISMATCH", "Challenge does not belong to this task");
    }
    if (!challenge.getUserId().equals(userId)) {
      throw new BusinessException("CHALLENGE_USER_MISMATCH", "Challenge does not belong to this user");
    }
    if (challenge.isVerified()) {
      throw new BusinessException("CHALLENGE_ALREADY_USED", "Challenge has already been used");
    }
    if (challenge.isExpired(Instant.now())) {
      throw new BusinessException("CHALLENGE_EXPIRED", "Signing challenge has expired. Please request a new one.");
    }
    if (challenge.getAttemptCount() >= MAX_ATTEMPTS) {
      throw new BusinessException("CHALLENGE_LOCKED", "Max challenge attempts exceeded (" + MAX_ATTEMPTS + ")");
    }

    if ("TOTP_STEPUP".equalsIgnoreCase(challenge.getChallengeType())) {
      if (authCode == null || authCode.isBlank()) {
        int attempts = attemptRecorder.recordFailedAttempt(challenge.getId());
        throw new BusinessException("TOTP_CODE_REQUIRED", "TOTP verification code is required. Attempts: " + attempts + "/" + MAX_ATTEMPTS);
      }
      var res = authClient.verifyTotp(apiKeyProperties.getEffectiveUserApiKey(), userId, new VerifyTotpReq(authCode.trim()));
      if (res == null || res.data() == null || !res.data().valid()) {
        int attempts = attemptRecorder.recordFailedAttempt(challenge.getId());
        throw new BusinessException("INVALID_TOTP_CODE", "Invalid TOTP verification code. Attempts: " + attempts + "/" + MAX_ATTEMPTS);
      }
    } else if ("DIGITAL_SIGNATURE_CA".equalsIgnoreCase(challenge.getChallengeType())) {
      String token = (authCode != null && !authCode.isBlank()) ? authCode : signatureReference;
      SignatureVerificationResult sigRes = signatureProvider.verifySignature(nonce, token, challenge.getPayloadHash());
      if (!sigRes.valid()) {
        int attempts = attemptRecorder.recordFailedAttempt(challenge.getId());
        throw new BusinessException("INVALID_DIGITAL_SIGNATURE", "CA Signature verification failed: " + sigRes.failureReason() + ". Attempts: " + attempts + "/" + MAX_ATTEMPTS);
      }
    }

    if (!attemptRecorder.redeem(challenge.getId(), Instant.now())) {
      throw new BusinessException("CHALLENGE_ALREADY_USED", "Challenge was expired, locked, or already used");
    }
    return challengeRepository.findById(challenge.getId()).orElseThrow(() ->
        new BusinessException("CHALLENGE_NOT_FOUND", "Signing challenge no longer exists"));
  }
}
