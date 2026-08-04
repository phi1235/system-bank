package com.banksystem.transaction.domain.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recon_runs")
public class ReconRunEntity {

  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_MATCHED = "MATCHED";
  public static final String STATUS_MISMATCHED = "MISMATCHED";
  public static final String STATUS_FAILED = "FAILED";

  public static final String TRIGGER_SCHEDULED = "SCHEDULED";
  public static final String TRIGGER_MANUAL = "MANUAL";

  @Id
  private UUID id;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(nullable = false, length = 40)
  private String zone;

  @Column(name = "trigger_type", nullable = false, length = 20)
  private String triggerType;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "orders_checked", nullable = false)
  private int ordersChecked;

  @Column(name = "ledger_entries_seen", nullable = false)
  private int ledgerEntriesSeen;

  @Column(name = "discrepancy_count", nullable = false)
  private int discrepancyCount;

  @Column(name = "error_detail", length = 500)
  private String errorDetail;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public LocalDate getBusinessDate() {
    return businessDate;
  }

  public void setBusinessDate(LocalDate businessDate) {
    this.businessDate = businessDate;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public void setTriggerType(String triggerType) {
    this.triggerType = triggerType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public int getOrdersChecked() {
    return ordersChecked;
  }

  public void setOrdersChecked(int ordersChecked) {
    this.ordersChecked = ordersChecked;
  }

  public int getLedgerEntriesSeen() {
    return ledgerEntriesSeen;
  }

  public void setLedgerEntriesSeen(int ledgerEntriesSeen) {
    this.ledgerEntriesSeen = ledgerEntriesSeen;
  }

  public int getDiscrepancyCount() {
    return discrepancyCount;
  }

  public void setDiscrepancyCount(int discrepancyCount) {
    this.discrepancyCount = discrepancyCount;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }
}
