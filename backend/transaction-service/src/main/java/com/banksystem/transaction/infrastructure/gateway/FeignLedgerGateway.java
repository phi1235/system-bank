package com.banksystem.transaction.infrastructure.gateway;

import com.banksystem.transaction.application.gateway.LedgerGateway;
import com.banksystem.transaction.infrastructure.feign.LedgerClient;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerEntryView;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerSearchRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignLedgerGateway implements LedgerGateway {

  private final LedgerClient ledgerClient;
  private final String internalApiKey;

  public FeignLedgerGateway(
      Optional<LedgerClient> ledgerClient,
      @Value("${bank.internal.account-api-key:internal-secret-key-12345}") String internalApiKey) {
    this.ledgerClient = ledgerClient.orElse(null);
    this.internalApiKey = internalApiKey;
  }

  @Override
  public List<LedgerEntryView> searchLedger(LedgerSearchRequest request) {
    if (ledgerClient == null) return List.of();
    var resp = ledgerClient.search(request, internalApiKey);
    return resp != null && resp.data() != null ? resp.data() : List.of();
  }
}
