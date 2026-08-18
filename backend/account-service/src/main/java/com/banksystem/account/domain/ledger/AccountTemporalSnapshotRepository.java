package com.banksystem.account.domain.ledger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTemporalSnapshotRepository
    extends JpaRepository<AccountTemporalSnapshotEntity, UUID> {
  Optional<AccountTemporalSnapshotEntity>
      findTopByAccountIdAndSnapshotAtLessThanEqualOrderBySnapshotAtDesc(UUID accountId, Instant at);
}
