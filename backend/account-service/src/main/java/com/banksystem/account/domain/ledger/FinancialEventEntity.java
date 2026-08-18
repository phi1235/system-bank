package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "financial_events")
public class FinancialEventEntity {
  @Id @Column(name = "event_id") private UUID eventId;
  @Column(name = "aggregate_type", nullable = false, length = 40) private String aggregateType;
  @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
  @Column(name = "sequence_no", nullable = false) private long sequenceNo;
  @Column(name = "event_type", nullable = false, length = 80) private String eventType;
  @Column(name = "schema_version", nullable = false) private int schemaVersion;
  @Column(name = "transaction_id") private UUID transactionId;
  @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb") private String payloadJson;
  @Column(name = "payload_sha256", nullable = false, length = 64) private String payloadSha256;

  public static FinancialEventEntity of(
      UUID eventId, UUID journalId, String eventType, UUID transactionId,
      Instant occurredAt, String payloadJson, String checksum) {
    FinancialEventEntity entity = new FinancialEventEntity();
    entity.eventId = eventId;
    entity.aggregateType = "JOURNAL";
    entity.aggregateId = journalId;
    entity.sequenceNo = 1;
    entity.eventType = eventType;
    entity.schemaVersion = 1;
    entity.transactionId = transactionId;
    entity.occurredAt = occurredAt;
    entity.payloadJson = payloadJson;
    entity.payloadSha256 = checksum;
    return entity;
  }

  public UUID getEventId() { return eventId; }
  public String getAggregateType() { return aggregateType; }
  public UUID getAggregateId() { return aggregateId; }
  public long getSequenceNo() { return sequenceNo; }
  public String getEventType() { return eventType; }
  public int getSchemaVersion() { return schemaVersion; }
  public UUID getTransactionId() { return transactionId; }
  public Instant getOccurredAt() { return occurredAt; }
  public String getPayloadJson() { return payloadJson; }
  public String getPayloadSha256() { return payloadSha256; }
}
