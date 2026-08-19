package com.banksystem.transaction.application.transfer;

import com.banksystem.transaction.domain.transfer.BankDirectoryEntity;
import com.banksystem.transaction.domain.transfer.BankDirectoryRepository;
import com.banksystem.transaction.domain.transfer.BankEntity;
import com.banksystem.transaction.domain.transfer.BankRepository;
import com.banksystem.transaction.domain.transfer.ProviderBankCapabilityEntity;
import com.banksystem.transaction.domain.transfer.ProviderBankCapabilityRepository;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankCatalogService {

  public record BankItem(
      String bankCode,
      String shortName,
      String fullName,
      String bin,
      String logoUrl,
      boolean napasSupported,
      boolean isInternal
  ) implements Serializable {}

  private final BankDirectoryRepository bankDirectoryRepository;
  private final BankRepository bankRepository;
  private final ProviderBankCapabilityRepository capabilityRepository;
  private final String payoutProvider;

  public BankCatalogService(
      BankDirectoryRepository bankDirectoryRepository,
      BankRepository bankRepository,
      ProviderBankCapabilityRepository capabilityRepository,
      @Value("${bank.napas.provider:mock}") String napasProvider) {
    this.bankDirectoryRepository = bankDirectoryRepository;
    this.bankRepository = bankRepository;
    this.capabilityRepository = capabilityRepository;
    this.payoutProvider = payoutProviderName(napasProvider);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "bankDirectory", key = "'active_banks'", unless = "#result == null || #result.isEmpty()")
  public List<BankItem> listBanks() {
    List<BankDirectoryEntity> dirList = bankDirectoryRepository.findByActiveTrueOrderByShortNameAsc();
    if (!dirList.isEmpty()) {
      Map<String, ProviderBankCapabilityEntity> capabilities = capabilityRepository
          .findByProvider(payoutProvider).stream()
          .collect(Collectors.toMap(ProviderBankCapabilityEntity::getBankBin, Function.identity()));
      return dirList.stream()
          .map(b -> {
            boolean internal = "970499".equals(b.getBin()) || "SYSTEM_BANK".equalsIgnoreCase(b.getCode());
            ProviderBankCapabilityEntity capability = capabilities.get(b.getBin());
            boolean supported = internal || (b.isLookupSupported()
                && capability != null
                && capability.isPayoutSupported()
                && "ACTIVE".equalsIgnoreCase(capability.getStatus()));
            return new BankItem(
                b.getCode(), b.getShortName(), b.getFullName(), b.getBin(),
                b.getLogoUrl() != null ? b.getLogoUrl() : "", supported, internal);
          })
          .toList();
    }

    // Fallback to legacy bank table if directory is empty
    List<BankEntity> list = bankRepository.findByStatusOrderByShortNameAsc("ACTIVE");
    return list.stream()
        .map(b -> new BankItem(
            b.getCode(),
            b.getShortName(),
            b.getFullName(),
            b.getBin(),
            b.getLogoUrl() != null ? b.getLogoUrl() : "",
            b.isNapasSupported(),
            b.isInternal()
        ))
        .toList();
  }

  private static String payoutProviderName(String configuredProvider) {
    String value = configuredProvider == null ? "mock" : configuredProvider.trim().toLowerCase(Locale.ROOT);
    return "http".equals(value) ? "NAPAS" : "MOCK_NAPAS";
  }
}
