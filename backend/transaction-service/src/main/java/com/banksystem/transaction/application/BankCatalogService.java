package com.banksystem.transaction.application;

import com.banksystem.transaction.domain.BankEntity;
import com.banksystem.transaction.domain.BankRepository;
import java.util.List;
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
  ) {}

  private final BankRepository bankRepository;

  public BankCatalogService(BankRepository bankRepository) {
    this.bankRepository = bankRepository;
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "napasBanks", key = "'all'")
  public List<BankItem> listBanks() {
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
}
