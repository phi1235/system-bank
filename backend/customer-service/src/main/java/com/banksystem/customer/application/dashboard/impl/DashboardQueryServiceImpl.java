package com.banksystem.customer.application.dashboard.impl;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.customer.api.dto.DashboardDtos.DashboardSummaryResponse;
import com.banksystem.customer.api.dto.DashboardDtos.InternalAccountCountsResponse;
import com.banksystem.customer.api.dto.DashboardDtos.InternalTransactionCountsResponse;
import com.banksystem.customer.api.dto.DashboardDtos.InternalUserCountsResponse;
import com.banksystem.customer.infrastructure.feign.DashboardClients.AccountCountsClient;
import com.banksystem.customer.infrastructure.feign.DashboardClients.TransactionCountsClient;
import com.banksystem.customer.infrastructure.feign.DashboardClients.UserCountsClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryServiceImpl implements DashboardQueryService {

  private static final Logger log = LoggerFactory.getLogger(DashboardQueryService.class);

  private final CustomerRepository customerRepository;
  private final AccountCountsClient accountClient;
  private final TransactionCountsClient transactionClient;
  private final UserCountsClient userClient;
  private final String accountApiKey;
  private final String transactionApiKey;
  private final String userApiKey;

  public DashboardQueryServiceImpl(
      CustomerRepository customerRepository,
      AccountCountsClient accountClient,
      TransactionCountsClient transactionClient,
      UserCountsClient userClient,
      @Value("${bank.internal.account-api-key:${bank.internal.api-key:}}") String accountApiKey,
      @Value("${bank.internal.transaction-api-key:${bank.internal.api-key:}}") String transactionApiKey,
      @Value("${bank.internal.user-api-key:${bank.internal.api-key:}}") String userApiKey) {
    this.customerRepository = customerRepository;
    this.accountClient = accountClient;
    this.transactionClient = transactionClient;
    this.userClient = userClient;
    this.accountApiKey = accountApiKey;
    this.transactionApiKey = transactionApiKey;
    this.userApiKey = userApiKey;
  }

  @Transactional(readOnly = true)
  public DashboardSummaryResponse getSummary() {
    long customers = customerRepository.count();
    long kycPending = customerRepository.countByKycStatus("PENDING");

    // Parallel Feign calls to avoid sequential blocking (fixes pending issue)
    var accountFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResponse<InternalAccountCountsResponse> res = accountClient.counts(accountApiKey);
        if (res != null && res.success() && res.data() != null) {
          return res.data();
        }
        log.warn("Account counts response invalid: success={}, data={}", 
            res != null ? res.success() : "null", res != null ? res.data() : "null");
      } catch (Exception ex) {
        log.error("Failed to fetch account counts: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
      }
      return new InternalAccountCountsResponse(0, 0);
    });

    var transactionFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResponse<InternalTransactionCountsResponse> res = transactionClient.counts(transactionApiKey);
        if (res != null && res.success() && res.data() != null) {
          return res.data();
        }
        log.warn("Transaction counts response invalid: success={}, data={}", 
            res != null ? res.success() : "null", res != null ? res.data() : "null");
      } catch (Exception ex) {
        log.error("Failed to fetch transaction counts: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
      }
      return new InternalTransactionCountsResponse(0, 0, 0, 0, 0, 0, 0);
    });

    var userFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResponse<InternalUserCountsResponse> res = userClient.counts(userApiKey);
        if (res != null && res.success() && res.data() != null) {
          return res.data();
        }
        log.warn("User counts response invalid: success={}, data={}",
            res != null ? res.success() : "null", res != null ? res.data() : "null");
      } catch (Exception ex) {
        log.error("Failed to fetch user counts: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
      }
      return new InternalUserCountsResponse(0, 0);
    });

    // Wait for all with generous timeout (90s covers heavy COUNT on 1M+ tables)
    InternalAccountCountsResponse acct;
    InternalTransactionCountsResponse txn;
    InternalUserCountsResponse usr;
    try {
      acct = accountFuture.get(90, TimeUnit.SECONDS);
      txn = transactionFuture.get(90, TimeUnit.SECONDS);
      usr = userFuture.get(90, TimeUnit.SECONDS);
    } catch (Exception ex) {
      log.error("Dashboard parallel fetch timed out or failed: {}", ex.getMessage());
      acct = accountFuture.getNow(new InternalAccountCountsResponse(0, 0));
      txn = transactionFuture.getNow(new InternalTransactionCountsResponse(0, 0, 0, 0, 0, 0, 0));
      usr = userFuture.getNow(new InternalUserCountsResponse(0, 0));
    }

    return new DashboardSummaryResponse(
        customers,
        kycPending,
        acct.total(),
        acct.frozen(),
        txn.transfers(),
        txn.transfersFailed(),
        txn.transfersCompensated(),
        txn.outboxDead(),
        txn.outboxPending(),
        txn.outboxPublished(),
        usr.users(),
        usr.usersLocked(),
        txn.audits());
  }
}
