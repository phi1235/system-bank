package com.banksystem.customer.domain.support;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_ticket_messages")
public class SupportTicketMessageEntity {

  @Id
  private UUID id;

  @Column(name = "ticket_id", nullable = false)
  private UUID ticketId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(name = "author_role", nullable = false, length = 20)
  private String authorRole;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTicketId() {
    return ticketId;
  }

  public void setTicketId(UUID ticketId) {
    this.ticketId = ticketId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public void setAuthorUserId(UUID authorUserId) {
    this.authorUserId = authorUserId;
  }

  public String getAuthorRole() {
    return authorRole;
  }

  public void setAuthorRole(String authorRole) {
    this.authorRole = authorRole;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
