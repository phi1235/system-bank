package com.banksystem.account.application.account;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.exception.BusinessException;
import java.util.UUID;
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
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
  }

  public AccountEntity requireOwnedOrStaff(UUID id, GatewayUser user) {
    AccountEntity a = require(id);
    boolean staffLookup = user.hasPermission("accounts:lookup:view")
        || user.hasPermission("accounts:freeze:execute");
    if (!staffLookup && !a.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your account");
    }
    return a;
  }

  public AccountStatus currentStatus(AccountEntity account) {
    return AccountStatus.tryParse(account.getStatus())
        .orElseThrow(() -> new BusinessException(
            "INVALID_ACCOUNT_STATE",
            "Account has unknown status: " + account.getStatus()));
  }
}
