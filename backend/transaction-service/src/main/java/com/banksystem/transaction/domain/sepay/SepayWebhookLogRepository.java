package com.banksystem.transaction.domain.sepay;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SepayWebhookLogRepository extends JpaRepository<SepayWebhookLog, UUID> {

  Optional<SepayWebhookLog> findBySepayTransactionId(Long sepayTransactionId);

  boolean existsBySepayTransactionId(Long sepayTransactionId);

  Optional<SepayWebhookLog> findFirstByCodeOrderByCreatedAtDesc(String code);
}
