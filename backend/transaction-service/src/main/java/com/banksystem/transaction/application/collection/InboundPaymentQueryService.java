package com.banksystem.transaction.application.collection;

import com.banksystem.transaction.api.dto.CollectionDtos.InboundPaymentEventResponse;
import com.banksystem.transaction.domain.collection.InboundPaymentEventRepository;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundPaymentQueryService {

  private final InboundPaymentEventRepository repository;

  public InboundPaymentQueryService(InboundPaymentEventRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Page<InboundPaymentEventResponse> search(InboundPaymentSearchQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    return search(query.provider(), query.q(), query.status(), pageable);
  }

  @Transactional(readOnly = true)
  public Page<InboundPaymentEventResponse> search(
      String provider, String query, InboundPaymentStatus status, Pageable pageable) {
    return repository.search(provider, query, status, pageable)
        .map(event -> new InboundPaymentEventResponse(
            event.getId(), event.getProvider(), event.getProviderTransactionId(),
            event.getVirtualAccountNumber(), event.getBankBin(), event.getAmount(),
            event.getCurrency(), event.getSenderAccount(), event.getSenderBankBin(),
            event.getSenderName(), event.getReferenceContent(), event.getStatus(),
            event.getErrorMessage(), event.getProcessedAt(), event.getCreatedAt()));
  }
}
