package com.banksystem.account.application;

import com.banksystem.account.domain.AccountRepository;
import com.banksystem.common.exception.BusinessException;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates unique internal account numbers (10 + 8 digits). */
@Component
public class AccountNumberGenerator {

  private final AccountRepository accountRepository;
  private final SecureRandom random = new SecureRandom();

  public AccountNumberGenerator(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public String next() {
    for (int i = 0; i < 20; i++) {
      int suffix = random.nextInt(100_000_000);
      String num = String.format("10%08d", suffix);
      if (!accountRepository.existsByAccountNumber(num)) {
        return num;
      }
    }
    throw new BusinessException("ACCOUNT_NUMBER_GEN_FAILED", "Could not generate unique account number");
  }
}
