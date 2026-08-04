package com.banksystem.transaction.domain.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recon_items")
public class ReconItemEntity {

  @Id
  private UUID id;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "transfer_id")
  private UUID transferId;

  @Column(nullable = false, length = 40)
  private String kind;

  @Column(name = "entry_ref", length = 80)
  private String entryRef;

  @Column(name = "expected_amount", precision = 19, scale = 2)
  private BigDecimal expectedAmount;

  @Column(name = "actual_amount", precision = 19, scale = 2)
  private BigDecimal actualAmount;

  @Column(length = 255)
  private String detail;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getRunId() {
    return runId;
  }

  public void setRunId(UUID runId) {
    this.runId = runId;
  }

  public UUID getTransferId() {
    return transferId;
  }

  public void setTransferId(UUID transferId) {
    this.transferId = transferId;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getEntryRef() {
    return entryRef;
  }

  public void setEntryRef(String entryRef) {
    this.entryRef = entryRef;
  }

  public BigDecimal getExpectedAmount() {
    return expectedAmount;
  }

  public void setExpectedAmount(BigDecimal expectedAmount) {
    this.expectedAmount = expectedAmount;
  }

  public BigDecimal getActualAmount() {
    return actualAmount;
  }

  public void setActualAmount(BigDecimal actualAmount) {
    this.actualAmount = actualAmount;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }
}
