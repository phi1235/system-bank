package com.banksystem.transaction.application.transfer;

import com.banksystem.transaction.domain.transfer.BankEntity;
import com.banksystem.transaction.domain.transfer.BankRepository;
import java.io.Serializable;
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
  ) implements Serializable {}

  private final BankRepository bankRepository;

  public BankCatalogService(BankRepository bankRepository) {
    this.bankRepository = bankRepository;
  }

  @Transactional(readOnly = true)
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
