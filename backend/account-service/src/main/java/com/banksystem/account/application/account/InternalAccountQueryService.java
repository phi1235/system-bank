package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.CreateCorporateAccountRequest;
import com.banksystem.account.api.dto.AccountDtos.InternalAccountCountsResponse;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalAccountQueryService {

  private static final String FROZEN_STATUS = "FROZEN";

  private final AccountRepository accountRepository;
  private final AccountMapper mapper;
  private final AccountNumberGenerator accountNumbers;

  public InternalAccountQueryService(
      AccountRepository accountRepository,
      AccountMapper mapper,
      AccountNumberGenerator accountNumbers) {
    this.accountRepository = accountRepository;
    this.mapper = mapper;
    this.accountNumbers = accountNumbers;
  }

  @Transactional(readOnly = true)
  public InternalAccountCountsResponse counts() {
    return new InternalAccountCountsResponse(
        accountRepository.count(),
        accountRepository.countByStatus(FROZEN_STATUS));
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> listByOwner(String ownerType, UUID ownerId) {
    return accountRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType.toUpperCase(), ownerId)
        .stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public AccountResponse createCorporateAccount(CreateCorporateAccountRequest req) {
    accountRepository.acquireCreationCommandLock(req.commandId().toString());
    String requestedAccountType = req.accountType() != null && !req.accountType().isBlank()
        ? req.accountType().trim().toUpperCase()
        : "CORPORATE_PAYMENT";
    String requestedCurrency = req.currency() != null && !req.currency().isBlank()
        ? req.currency().trim().toUpperCase()
        : "VND";
    AccountEntity existing = accountRepository.findByCreationCommandId(req.commandId()).orElse(null);
    if (existing != null) {
      if (!"CORPORATE".equals(existing.getOwnerType())
          || !req.corporateId().equals(existing.getOwnerId())
          || !req.createdByUserId().equals(existing.getUserId())
          || !requestedAccountType.equalsIgnoreCase(existing.getAccountType())
          || !requestedCurrency.equalsIgnoreCase(existing.getCurrency())) {
        throw new BusinessException(
            "IDEMPOTENCY_CONFLICT", "Command id was already used for another account owner");
      }
      return mapper.toResponse(existing);
    }

    AccountEntity a = new AccountEntity();
    a.setId(UUID.randomUUID());
    a.setUserId(req.createdByUserId());
    a.setOwnerType("CORPORATE");
    a.setOwnerId(req.corporateId());
    a.setCreationCommandId(req.commandId());
    a.setAccountNumber(accountNumbers.next());
    a.setAccountType(requestedAccountType);
    a.setCurrency(requestedCurrency);
    a.setBalance(BigDecimal.ZERO);
    a.setStatus("ACTIVE");
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());

    AccountEntity saved = accountRepository.save(a);
    return mapper.toResponse(saved);
  }
}
