package com.banksystem.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_step_logs")
public class SagaStepLogEntity {

  @Id
  private UUID id;

  @Column(name = "transfer_id", nullable = false)
  private UUID transferId;

  @Column(nullable = false, length = 50)
  private String step;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public static SagaStepLogEntity of(UUID transferId, String step, String status, String detail) {
    SagaStepLogEntity e = new SagaStepLogEntity();
    e.id = UUID.randomUUID();
    e.transferId = transferId;
    e.step = step;
    e.status = status;
    e.detail = detail;
    e.createdAt = Instant.now();
    return e;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTransferId() {
    return transferId;
  }

  public String getStep() {
    return step;
  }

  public String getStatus() {
    return status;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
