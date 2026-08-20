package com.banksystem.transaction.domain.merchant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantWebhookEndpointRepository extends JpaRepository<MerchantWebhookEndpointEntity, UUID> {
  List<MerchantWebhookEndpointEntity> findByOrganizationId(UUID organizationId);
  List<MerchantWebhookEndpointEntity> findByOrganizationIdAndStatus(UUID organizationId, String status);
}
