package com.banksystem.transaction.domain.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalBankAccountRepository extends JpaRepository<ExternalBankAccountEntity, UUID> {
  Optional<ExternalBankAccountEntity> findByBankCodeAndAccountNumber(String bankCode, String accountNumber);
}
