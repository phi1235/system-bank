package com.banksystem.corporate.application.receipt;

import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptArtifactLinkService {

  private final PayoutItemRepository itemRepository;

  public ReceiptArtifactLinkService(PayoutItemRepository itemRepository) {
    this.itemRepository = itemRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void link(UUID itemId, UUID artifactId) {
    itemRepository.findById(itemId).ifPresent(item -> {
      item.setReceiptArtifactId(artifactId);
      item.setUpdatedAt(Instant.now());
      itemRepository.saveAndFlush(item);
    });
  }
}
