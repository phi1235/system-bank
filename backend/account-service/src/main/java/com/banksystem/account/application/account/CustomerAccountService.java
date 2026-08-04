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

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.api.PageResponse;
import java.util.List;
import java.util.UUID;

public interface CustomerAccountService {
  AccountResponse open(UUID userId, OpenAccountRequest req);
  List<AccountResponse> listMine(UUID userId);
  AccountResponse get(UUID id, GatewayUser user);
  PageResponse<LedgerEntryResponse> statement(LedgerStatementQuery query, GatewayUser user);
  byte[] exportStatementCsv(LedgerStatementQuery query, GatewayUser user);
}
