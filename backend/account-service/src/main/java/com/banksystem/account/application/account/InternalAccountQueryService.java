package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.AccountDtos.InternalAccountCountsResponse;
import com.banksystem.account.domain.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalAccountQueryService {

  private static final String FROZEN_STATUS = "FROZEN";

  private final AccountRepository accountRepository;

  public InternalAccountQueryService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Transactional(readOnly = true)
  public InternalAccountCountsResponse counts() {
    return new InternalAccountCountsResponse(
        accountRepository.count(),
        accountRepository.countByStatus(FROZEN_STATUS));
  }
}
