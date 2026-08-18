package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "forensic_copilot_messages")
public class ForensicCopilotMessageEntity {
  @Id private UUID id;
  @Column(name = "session_id", nullable = false) private UUID sessionId;
  @Column(nullable = false, length = 20) private String role;
  @Column(nullable = false, columnDefinition = "TEXT") private String content;
  @Column(name = "response_status", length = 40) private String responseStatus;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_calls_json", nullable = false, columnDefinition = "jsonb") private String toolCallsJson;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citations_json", nullable = false, columnDefinition = "jsonb") private String citationsJson;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "validation_json", nullable = false, columnDefinition = "jsonb") private String validationJson;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public static ForensicCopilotMessageEntity of(
      UUID sessionId, String role, String content, String responseStatus,
      String tools, String citations, String validation, Instant now) {
    ForensicCopilotMessageEntity entity = new ForensicCopilotMessageEntity();
    entity.id = UUID.randomUUID();
    entity.sessionId = sessionId;
    entity.role = role;
    entity.content = content;
    entity.responseStatus = responseStatus;
    entity.toolCallsJson = tools;
    entity.citationsJson = citations;
    entity.validationJson = validation;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public UUID getSessionId() { return sessionId; }
  public String getRole() { return role; }
  public String getContent() { return content; }
  public String getResponseStatus() { return responseStatus; }
  public String getToolCallsJson() { return toolCallsJson; }
  public String getCitationsJson() { return citationsJson; }
  public String getValidationJson() { return validationJson; }
  public Instant getCreatedAt() { return createdAt; }
}
