package com.banksystem.transaction.domain.bill;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillCustomerRepository extends JpaRepository<BillCustomerEntity, UUID> {
  Optional<BillCustomerEntity> findByProviderIdAndCustomerCode(String providerId, String customerCode);
}
