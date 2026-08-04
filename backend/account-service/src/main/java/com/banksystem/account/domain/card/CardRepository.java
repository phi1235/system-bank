package com.banksystem.account.domain.card;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<CardEntity, UUID> {

  List<CardEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<CardEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

  boolean existsByAccountIdAndStatusNotIn(UUID accountId, Collection<CardStatus> statuses);

  /** Approval queue / admin browse, oldest request first. */
  Page<CardEntity> findByStatusOrderByCreatedAtAsc(CardStatus status, Pageable pageable);
}
