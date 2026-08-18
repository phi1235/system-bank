package com.banksystem.account.domain.ledger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerJournalRepository extends JpaRepository<LedgerJournalEntity, UUID> {
  Optional<LedgerJournalEntity> findByBusinessCommandId(String businessCommandId);
  List<LedgerJournalEntity> findByBusinessReferenceInOrderByCreatedAtAsc(
      Collection<String> references);
  List<LedgerJournalEntity> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

  @Query(value = """
      SELECT COALESCE(MAX(sequence_no), 0)
      FROM ledger_journals
      WHERE transaction_id = :transactionId AND journal_type = :journalType
      """, nativeQuery = true)
  int maxSequence(
      @Param("transactionId") UUID transactionId,
      @Param("journalType") String journalType);
}
