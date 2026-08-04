package com.banksystem.account.infrastructure.gateway;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.application.gateway.CustomerGateway;
import com.banksystem.account.infrastructure.feign.CustomerClient;
import com.banksystem.account.infrastructure.feign.CustomerClient.CustomerNameView;
import com.banksystem.account.infrastructure.feign.CustomerClient.CustomerNamesRequest;
import com.banksystem.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignCustomerGateway implements CustomerGateway {

  private static final Logger log = LoggerFactory.getLogger(FeignCustomerGateway.class);

  private final CustomerClient customerClient;
  private final String customerApiKey;

  public FeignCustomerGateway(
      Optional<CustomerClient> customerClient,
      @Value("${bank.internal.customer-api-key:internal-secret-key-12345}") String customerApiKey) {
    this.customerClient = customerClient.orElse(null);
    this.customerApiKey = customerApiKey;
  }

  @Override
  public Map<String, String> getCustomerNames(List<UUID> userIds) {
    if (customerClient == null || userIds == null || userIds.isEmpty()) return Map.of();
    try {
      ApiResponse<List<CustomerNameView>> res = customerClient.names(new CustomerNamesRequest(userIds), customerApiKey);
      if (res == null || !res.success() || res.data() == null) {
        return Map.of();
      }
      return res.data().stream().collect(Collectors.toMap(CustomerNameView::userId, CustomerNameView::fullName, (a, b) -> a));
    } catch (Exception ex) {
      log.warn("Owner-name enrichment failed: {}", ex.getMessage());
      return Map.of();
    }
  }
}
