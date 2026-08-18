package com.banksystem.transaction.domain.sepay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SepayPaymentOrderRepository extends JpaRepository<SepayPaymentOrder, UUID> {

  Optional<SepayPaymentOrder> findByOrderCode(String orderCode);

  Optional<SepayPaymentOrder> findBySepayTransactionId(Long sepayTransactionId);

  List<SepayPaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<SepayPaymentOrder> findByStatusAndCreatedAtBefore(SepayOrderStatus status, Instant createdAt);

  List<SepayPaymentOrder> findByStatusAndExpiresAtBefore(SepayOrderStatus status, Instant expiresAt);
}
