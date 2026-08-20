package com.banksystem.transaction.application.transfer;

import com.banksystem.transaction.domain.transfer.BankDirectoryEntity;
import com.banksystem.transaction.domain.transfer.BankDirectoryRepository;
import com.banksystem.transaction.domain.transfer.ProviderBankCapabilityEntity;
import com.banksystem.transaction.domain.transfer.ProviderBankCapabilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BankDirectorySyncJob {

  private static final Logger log = LoggerFactory.getLogger(BankDirectorySyncJob.class);

  private final String vietqrBaseUrl;
  private final BankDirectoryRepository bankDirectoryRepository;
  private final ProviderBankCapabilityRepository capabilityRepository;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public BankDirectorySyncJob(
      @Value("${bank.directory.sync.url}") String vietqrBaseUrl,
      BankDirectoryRepository bankDirectoryRepository,
      ProviderBankCapabilityRepository capabilityRepository,
      ObjectMapper objectMapper) {
    this.vietqrBaseUrl = vietqrBaseUrl;
    this.bankDirectoryRepository = bankDirectoryRepository;
    this.capabilityRepository = capabilityRepository;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(cron = "${bank.directory.sync.cron}")
  @Transactional
  @CacheEvict(value = "bankDirectory", allEntries = true)
  public void syncBankDirectory() {
    log.info("Starting daily bank directory synchronization from {}", vietqrBaseUrl);
    Instant now = Instant.now();

    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(vietqrBaseUrl))
          .timeout(Duration.ofSeconds(10))
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.warn("Bank directory sync received HTTP {}. Retaining last known good directory.", response.statusCode());
        recordSyncFailure("HTTP status " + response.statusCode());
        return;
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode dataArray = root.path("data");

      if (!dataArray.isArray() || dataArray.isEmpty()) {
        log.warn("Bank directory sync returned empty data array. Retaining existing records.");
        return;
      }

      long activeExternalBefore = bankDirectoryRepository.findByActiveTrueOrderByShortNameAsc().stream()
          .filter(bank -> !"970499".equals(bank.getBin()))
          .count();
      Set<String> seenBins = new HashSet<>();
      int syncedCount = 0;
      for (JsonNode item : dataArray) {
        String bin = item.path("bin").asText("");
        String code = item.path("code").asText("");
        String shortName = item.path("shortName").asText("");
        String fullName = item.path("name").asText(shortName);
        String logoUrl = item.path("logo").asText("");
        boolean lookupSupported = item.path("lookupSupported").asInt(0) == 1;
        boolean qrTransferSupported = item.path("transferSupported").asInt(0) == 1;

        if (bin.isBlank() || code.isBlank() || shortName.isBlank()) {
          continue;
        }
        seenBins.add(bin);

        // Upsert Bank Directory
        Optional<BankDirectoryEntity> existing = bankDirectoryRepository.findByBin(bin);
        BankDirectoryEntity bankEntity;
        if (existing.isPresent()) {
          bankEntity = existing.get();
          bankEntity.setCode(code);
          bankEntity.setShortName(shortName);
          bankEntity.setFullName(fullName);
          if (!logoUrl.isBlank()) {
            bankEntity.setLogoUrl(logoUrl);
          }
          bankEntity.setLastSyncedAt(now);
          bankEntity.setLastSyncStatus("SUCCESS");
          bankEntity.setLastSyncError(null);
          bankEntity.setActive(true);
          bankEntity.setLookupSupported(lookupSupported);
          bankEntity.setQrTransferSupported(qrTransferSupported);
          bankEntity.setUpdatedAt(now);
        } else {
          bankEntity = new BankDirectoryEntity(
              UUID.randomUUID(), bin, code, shortName, fullName, logoUrl, true);
          bankEntity.setLastSyncedAt(now);
          bankEntity.setLastSyncStatus("SUCCESS");
          bankEntity.setLookupSupported(lookupSupported);
          bankEntity.setQrTransferSupported(qrTransferSupported);
        }
        bankDirectoryRepository.save(bankEntity);
        upsertCapability("VIETQR", bin, lookupSupported, false, "VIETQR_DIRECTORY", now);
        upsertCapability("MOCK", bin, lookupSupported, false, "LOCAL_SIMULATOR", now);
        upsertCapability("MOCK_NAPAS", bin, false, true, "LOCAL_SIMULATOR", now);
        syncedCount++;
      }

      // Treat a sufficiently complete successful response as authoritative. Failed,
      // empty, or suspiciously partial responses retain the last-known-good set.
      long minimumCompleteCount = Math.max(1L, (long) Math.ceil(activeExternalBefore * 0.8d));
      if (syncedCount >= minimumCompleteCount) {
        bankDirectoryRepository.findByActiveTrueOrderByShortNameAsc().stream()
            .filter(bank -> !"970499".equals(bank.getBin()))
            .filter(bank -> !seenBins.contains(bank.getBin()))
            .forEach(bank -> {
              bank.setActive(false);
              bank.setLastSyncedAt(now);
              bank.setLastSyncStatus("REMOVED");
              bank.setLastSyncError("Bank absent from latest complete VietQR directory");
              bank.setUpdatedAt(now);
              bankDirectoryRepository.save(bank);
            });
      } else {
        log.warn(
            "Bank directory response looked partial (synced={}, expected-at-least={}); retaining missing banks",
            syncedCount, minimumCompleteCount);
      }

      log.info("Successfully synchronized {} banks into Bank Directory with Last-Known-Good protection", syncedCount);

    } catch (Exception ex) {
      log.error("Bank directory sync failed with error: {}. Retaining Last-Known-Good directory.", ex.getMessage());
      recordSyncFailure(ex.getMessage());
    }
  }

  private void upsertCapability(
      String provider,
      String bankBin,
      boolean inquirySupported,
      boolean payoutSupported,
      String source,
      Instant now) {
    ProviderBankCapabilityEntity capability = capabilityRepository
        .findByProviderAndBankBin(provider, bankBin)
        .orElseGet(() -> new ProviderBankCapabilityEntity(
            UUID.randomUUID(), provider, bankBin, inquirySupported, payoutSupported,
            "ACTIVE", source));
    capability.setInquirySupported(inquirySupported);
    capability.setPayoutSupported(payoutSupported);
    capability.setStatus("ACTIVE");
    capability.setSource(source);
    capability.setLastCheckedAt(now);
    capability.setUpdatedAt(now);
    capabilityRepository.save(capability);
  }

  private void recordSyncFailure(String errorMessage) {
    bankDirectoryRepository.findByBin("970499").ifPresent(b -> {
      b.setLastSyncStatus("FAILED");
      b.setLastSyncError(errorMessage);
      bankDirectoryRepository.save(b);
    });
  }
}
