package com.banksystem.transaction.application.merchant;

import com.banksystem.transaction.domain.merchant.MerchantAccountEntity;
import com.banksystem.transaction.domain.merchant.MerchantAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantAccountTransactionService {

  private final MerchantAccountRepository repository;

  public MerchantAccountTransactionService(MerchantAccountRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MerchantAccountEntity upsert(
      UUID businessId,
      UUID collectionAccountId,
      UUID escrowAccountId,
      UUID commissionAccountId,
      String currency) {
    Instant now = Instant.now();
    MerchantAccountEntity entity = repository.findByOrganizationId(businessId)
        .orElseGet(() -> MerchantAccountEntity.create(
            businessId, collectionAccountId, escrowAccountId, commissionAccountId, currency, now));
    entity.setCollectionAccountId(collectionAccountId);
    entity.setEscrowAccountId(escrowAccountId);
    entity.setCommissionAccountId(commissionAccountId);
    entity.setDefaultCurrency(currency);
    entity.setUpdatedAt(now);
    return repository.save(entity);
  }
}
