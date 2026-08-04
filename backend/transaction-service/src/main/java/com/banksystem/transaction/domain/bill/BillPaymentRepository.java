package com.banksystem.transaction.domain.bill;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillPaymentRepository extends JpaRepository<BillPaymentEntity, UUID> {
  Page<BillPaymentEntity> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
