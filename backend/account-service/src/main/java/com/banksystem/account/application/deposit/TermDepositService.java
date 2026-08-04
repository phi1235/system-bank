package com.banksystem.account.application.deposit;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.DepositQuoteResponse;
import com.banksystem.account.api.dto.DepositDtos.OpenDepositRequest;
import com.banksystem.account.api.dto.DepositDtos.TermDepositResponse;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TermDepositService {
  List<DepositProductResponse> products();
  DepositQuoteResponse quote(String productCode, BigDecimal amount);
  TermDepositResponse open(OpenDepositRequest request, GatewayUser user);
  TermDepositResponse closeEarly(UUID depositId, GatewayUser user);
  boolean mature(UUID depositId);
  List<TermDepositResponse> listMine(UUID userId);
  TermDepositResponse get(UUID depositId, GatewayUser user);
}
