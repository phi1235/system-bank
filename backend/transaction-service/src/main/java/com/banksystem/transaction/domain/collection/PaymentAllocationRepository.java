package com.banksystem.transaction.domain.collection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocationEntity, UUID> {
  List<PaymentAllocationEntity> findByCollectionOrderId(UUID collectionOrderId);
  List<PaymentAllocationEntity> findByInboundPaymentEventId(UUID inboundPaymentEventId);
}
