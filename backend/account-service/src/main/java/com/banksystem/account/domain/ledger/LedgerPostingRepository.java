package com.banksystem.account.domain.ledger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerPostingRepository extends JpaRepository<LedgerPostingEntity, UUID> {
  List<LedgerPostingEntity> findByJournalIdOrderByCreatedAtAsc(UUID journalId);
  List<LedgerPostingEntity> findByJournalIdInOrderByCreatedAtAsc(Collection<UUID> journalIds);
}
