package com.banksystem.transaction.application.gateway;

import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerEntryView;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerSearchRequest;
import java.util.List;

public interface LedgerGateway {
  List<LedgerEntryView> searchLedger(LedgerSearchRequest request);
}
