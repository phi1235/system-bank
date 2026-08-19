package com.banksystem.transaction.domain.transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderBankCapabilityRepository extends JpaRepository<ProviderBankCapabilityEntity, UUID> {

  Optional<ProviderBankCapabilityEntity> findByProviderAndBankBin(String provider, String bankBin);

  List<ProviderBankCapabilityEntity> findByProvider(String provider);
}
