package com.banksystem.transaction.domain.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_allocations")
public class PaymentAllocationEntity {

  @Id
  private UUID id;

  @Column(name = "inbound_payment_event_id", nullable = false)
  private UUID inboundPaymentEventId;

  @Column(name = "collection_order_id", nullable = false)
  private UUID collectionOrderId;

  @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal allocatedAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public static PaymentAllocationEntity create(
      UUID inboundPaymentEventId, UUID collectionOrderId, BigDecimal allocatedAmount, Instant now) {
    PaymentAllocationEntity entity = new PaymentAllocationEntity();
    entity.id = UUID.randomUUID();
    entity.inboundPaymentEventId = inboundPaymentEventId;
    entity.collectionOrderId = collectionOrderId;
    entity.allocatedAmount = allocatedAmount;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getInboundPaymentEventId() { return inboundPaymentEventId; }
  public void setInboundPaymentEventId(UUID inboundPaymentEventId) { this.inboundPaymentEventId = inboundPaymentEventId; }
  public UUID getCollectionOrderId() { return collectionOrderId; }
  public void setCollectionOrderId(UUID collectionOrderId) { this.collectionOrderId = collectionOrderId; }
  public BigDecimal getAllocatedAmount() { return allocatedAmount; }
  public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
