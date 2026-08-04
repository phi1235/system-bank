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
import com.banksystem.account.api.dto.AccountDtos.AdminAccountFilterRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.GatewayUser;
import java.util.List;
import java.util.UUID;

public interface AdminAccountService {
  PageResponse<AccountResponse> adminList(AdminAccountSearchQuery query);
  Object adminList(AdminAccountFilterRequest req);
  Object adminList(AdminAccountSearchQuery query, boolean noCount);
  List<AccountResponse> adminListSlice(AdminAccountSearchQuery query);
  AccountResponse get(UUID id);
  AccountResponse freeze(UUID id);
  AccountResponse freeze(UUID id, GatewayUser actor);
  AccountResponse unfreeze(UUID id);
  AccountResponse unfreeze(UUID id, GatewayUser actor);
  TopUpResponse topUp(UUID id, TopUpRequest req, GatewayUser actor);
}
