package com.banksystem.customer.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessageEntity, UUID> {

  List<SupportTicketMessageEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
