package com.banksystem.transaction.domain.virtualaccount;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VirtualAccountPoolRepository extends JpaRepository<VirtualAccountPoolEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM VirtualAccountPoolEntity p WHERE p.provider = :provider AND p.bankBin = :bankBin AND p.status = 'ACTIVE'")
  Optional<VirtualAccountPoolEntity> findActivePoolForUpdate(
      @Param("provider") String provider,
      @Param("bankBin") String bankBin);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM VirtualAccountPoolEntity p WHERE p.provider = :provider AND p.status = 'ACTIVE'")
  Optional<VirtualAccountPoolEntity> findFirstActiveByProviderForUpdate(@Param("provider") String provider);
}
