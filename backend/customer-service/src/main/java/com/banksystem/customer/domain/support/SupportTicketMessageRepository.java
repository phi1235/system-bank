package com.banksystem.customer.domain.support;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessageEntity, UUID> {

  List<SupportTicketMessageEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
