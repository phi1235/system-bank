package com.banksystem.corporate.domain.corporation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CorporateAccountRepository extends JpaRepository<CorporateAccountEntity, UUID> {
  List<CorporateAccountEntity> findByCorporateIdAndStatus(UUID corporateId, String status);
  List<CorporateAccountEntity> findByCorporateId(UUID corporateId);
  Optional<CorporateAccountEntity> findByCorporateIdAndAccountId(UUID corporateId, UUID accountId);
  Optional<CorporateAccountEntity> findByCorporateIdAndAccountNumber(UUID corporateId, String accountNumber);
  boolean existsByCorporateIdAndAccountId(UUID corporateId, UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM CorporateAccountEntity a WHERE a.corporateId = :corporateId AND a.accountId = :accountId")
  Optional<CorporateAccountEntity> findLinkedAccountForUpdate(
      @Param("corporateId") UUID corporateId, @Param("accountId") UUID accountId);
}
