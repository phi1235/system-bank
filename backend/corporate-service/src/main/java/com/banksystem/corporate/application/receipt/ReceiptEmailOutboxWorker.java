package com.banksystem.corporate.application.receipt;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import com.banksystem.corporate.domain.receipt.ReceiptArtifactEntity;
import com.banksystem.corporate.domain.receipt.ReceiptArtifactRepository;
import com.banksystem.corporate.infrastructure.config.InternalApiKeyProperties;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.QueueEmailRequest;
import com.banksystem.corporate.infrastructure.feign.NotificationClient;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReceiptEmailOutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(ReceiptEmailOutboxWorker.class);
  private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,###");

  private final ReceiptArtifactRepository receiptRepository;
  private final PayoutItemRepository itemRepository;
  private final PayoutBatchRepository batchRepository;
  private final ReceiptService receiptService;
  private final ReceiptEmailStateService stateService;
  private final NotificationClient notificationClient;
  private final InternalApiKeyProperties apiKeyProperties;
  private final int maxRetries;
  private final int batchSize;
  private final String workerId = "RECEIPT-EMAIL-" + UUID.randomUUID();

  public ReceiptEmailOutboxWorker(
      ReceiptArtifactRepository receiptRepository,
      PayoutItemRepository itemRepository,
      PayoutBatchRepository batchRepository,
      ReceiptService receiptService,
      ReceiptEmailStateService stateService,
      NotificationClient notificationClient,
      InternalApiKeyProperties apiKeyProperties,
      @Value("${bank.payout.receipt-email-max-retries:10}") int maxRetries,
      @Value("${bank.payout.receipt-email-batch-size:50}") int batchSize) {
    this.receiptRepository = receiptRepository;
    this.itemRepository = itemRepository;
    this.batchRepository = batchRepository;
    this.receiptService = receiptService;
    this.stateService = stateService;
    this.notificationClient = notificationClient;
    this.apiKeyProperties = apiKeyProperties;
    this.maxRetries = maxRetries;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${bank.payout.receipt-email-delay-ms:5000}")
  public void dispatchPendingEmails() {
    Instant now = Instant.now();
    List<UUID> ids = stateService.claim(now, batchSize, workerId, now.plusSeconds(60));
    ids.forEach(this::dispatch);
  }

  private void dispatch(UUID artifactId) {
    try {
      ReceiptArtifactEntity artifact = receiptRepository.findById(artifactId).orElseThrow();
      PayoutItemEntity item = itemRepository.findById(artifact.getItemId()).orElseThrow();
      PayoutBatchEntity batch = batchRepository.findById(artifact.getBatchId()).orElseThrow();
      byte[] pdf = receiptService.downloadArtifact(artifact);
      String body = "Chi trả lương cho: " + item.getBeneficiaryName() + ". Số tiền: "
          + MONEY_FORMAT.format(item.getAmount()) + " " + item.getCurrency()
          + ". Mã biên lai: " + artifact.getId();
      QueueEmailRequest request = new QueueEmailRequest(
          artifact.getId(),
          artifact.getEmailRecipient(),
          "Biên lai chi trả " + batch.getBatchName(),
          body,
          "bien-lai-" + artifact.getId() + ".pdf",
          Base64.getEncoder().encodeToString(pdf));
      var response = notificationClient.queueEmail(
          apiKeyProperties.getEffectiveNotificationApiKey(), request);
      if (response == null || !Boolean.TRUE.equals(response.data())) {
        throw new IllegalStateException("Notification service did not accept the receipt email command");
      }
      stateService.markQueued(artifactId);
    } catch (RuntimeException exception) {
      log.warn("[RECEIPT-EMAIL-RETRY] Failed to enqueue receipt email [{}]: {}",
          artifactId, exception.getMessage());
      stateService.markFailed(artifactId, exception.getMessage(), maxRetries);
    }
  }
}
