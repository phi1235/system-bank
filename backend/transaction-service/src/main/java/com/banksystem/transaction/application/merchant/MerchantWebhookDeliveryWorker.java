package com.banksystem.transaction.application.merchant;

import com.banksystem.common.security.CryptoUtils;
import com.banksystem.transaction.application.merchant.MerchantWebhookDeliveryTransactionService.DeliveryClaimContext;
import com.banksystem.transaction.domain.merchant.MerchantWebhookEndpointEntity;
import com.banksystem.transaction.domain.merchant.MerchantWebhookEndpointRepository;
import com.banksystem.transaction.infrastructure.security.SsrfSafeHttpClient;
import com.banksystem.transaction.infrastructure.security.SsrfSafeHttpClient.HttpResponseDto;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MerchantWebhookDeliveryWorker {

  private static final Logger log = LoggerFactory.getLogger(MerchantWebhookDeliveryWorker.class);

  private final MerchantWebhookDeliveryTransactionService deliveryTxService;
  private final MerchantWebhookEndpointRepository endpointRepository;
  private final SsrfSafeHttpClient httpClient;

  @Value("${bank.aes.secret-key}")
  private String aesSecretKey;

  public MerchantWebhookDeliveryWorker(
      MerchantWebhookDeliveryTransactionService deliveryTxService,
      MerchantWebhookEndpointRepository endpointRepository,
      SsrfSafeHttpClient httpClient) {
    this.deliveryTxService = deliveryTxService;
    this.endpointRepository = endpointRepository;
    this.httpClient = httpClient;
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 5000)
  public void processPendingDeliveries() {
    // Phase 1 (TX A): Claim batch with lease
    List<DeliveryClaimContext> claimedList = deliveryTxService.claimDeliveriesBatch(20, 60);

    if (claimedList.isEmpty()) {
      return;
    }

    for (DeliveryClaimContext delivery : claimedList) {
      deliverSingleOutsideTransaction(delivery);
    }
  }

  private void deliverSingleOutsideTransaction(DeliveryClaimContext delivery) {
    Instant now = Instant.now();
    Optional<MerchantWebhookEndpointEntity> endpointOpt = endpointRepository.findById(delivery.endpointId());
    if (endpointOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(endpointOpt.get().getStatus())) {
      deliveryTxService.markDeadLetterEndpointInactive(
          delivery.deliveryId(), delivery.claimToken(), "Endpoint inactive or deleted");
      return;
    }

    MerchantWebhookEndpointEntity endpoint = endpointOpt.get();
    String rawSecret;
    try {
      if (aesSecretKey == null || aesSecretKey.isBlank()) {
        deliveryTxService.markDeadLetterEndpointInactive(
            delivery.deliveryId(), delivery.claimToken(), "SECURITY_ERROR: Server AES key not configured");
        return;
      }
      rawSecret = CryptoUtils.decrypt(endpoint.getEncryptedSecret(), aesSecretKey);
    } catch (Exception e) {
      log.error("[MERCHANT-WEBHOOK-DELIVERY] Decryption failed for endpoint {}: {}", endpoint.getId(), e.getMessage());
      deliveryTxService.markDeadLetterEndpointInactive(
          delivery.deliveryId(), delivery.claimToken(), "SECURITY_ERROR: Decryption failed for endpoint secret");
      return;
    }

    long timestamp = now.toEpochMilli();
    String signatureData = timestamp + "\n" + delivery.payload();
    String signature = hmacSha256(signatureData, rawSecret);

    try {
      // Phase 2: HTTP POST outside any DB transaction
      HttpResponseDto response = httpClient.sendWebhook(endpoint.getUrl(), delivery.payload(), signature, timestamp);

      // Phase 3 (TX B): Finalize status based on HTTP code
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        deliveryTxService.markSuccess(
            delivery.deliveryId(), delivery.claimToken(), response.statusCode(), response.body());
      } else {
        deliveryTxService.markRetryOrDeadLetter(delivery.deliveryId(), delivery.claimToken(),
            response.statusCode(), response.body(),
            "Non-2xx HTTP status: " + response.statusCode());
      }
    } catch (Exception ex) {
      log.warn("[MERCHANT-WEBHOOK-DELIVERY] Delivery failed for event {} to endpoint {}: {}",
          delivery.eventId(), endpoint.getId(), ex.getMessage());
      deliveryTxService.markRetryOrDeadLetter(
          delivery.deliveryId(), delivery.claimToken(), null, null, ex.getMessage());
    }
  }

  private String hmacSha256(String data, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(rawHmac);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
    }
  }
}
