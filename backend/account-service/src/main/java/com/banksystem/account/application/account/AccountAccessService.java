package com.banksystem.account.application.account;

import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.enums.account.AccountStatus;
import com.banksystem.account.domain.repository.account.AccountRepository;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Shared account access helpers used by customer/admin/money use-cases.
 * Keeps ownership and status parsing out of fat application services.
 */
@Service
public class AccountAccessService {

  private final AccountRepository accountRepository;

  public AccountAccessService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public AccountEntity require(UUID id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found",
            HttpStatus.NOT_FOUND));
  }

  public AccountEntity requireOwnedOrStaff(UUID id, GatewayUser user) {
    AccountEntity a = require(id);
    boolean staffLookup = user.hasPermission("accounts:lookup:view")
        || user.hasPermission("accounts:freeze:execute");
    if (!staffLookup && !a.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your account", HttpStatus.FORBIDDEN);
    }
    return a;
  }

  public AccountStatus currentStatus(AccountEntity account) {
    return AccountStatus.tryParse(account.getStatus())
        .orElseThrow(() -> new BusinessException(
            "INVALID_ACCOUNT_STATE",
            "Account has unknown status: " + account.getStatus(),
            HttpStatus.INTERNAL_SERVER_ERROR));
  }
}
