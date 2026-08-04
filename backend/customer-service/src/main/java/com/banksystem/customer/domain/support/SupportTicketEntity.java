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
@Table(name = "support_tickets")
public class SupportTicketEntity {

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 40)
  private String category;

  @Column(nullable = false, length = 200)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false, length = 20)
  private String priority = "NORMAL";

  @Column(nullable = false, length = 20)
  private String status = "OPEN";

  @Column(name = "requester_email")
  private String requesterEmail;

  @Column(name = "resolution_note", columnDefinition = "TEXT")
  private String resolutionNote;

  @Column(name = "reject_reason", columnDefinition = "TEXT")
  private String rejectReason;

  @Column(name = "assigned_to")
  private UUID assignedTo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "rejected_by")
  private UUID rejectedBy;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRequesterEmail() {
    return requesterEmail;
  }

  public void setRequesterEmail(String requesterEmail) {
    this.requesterEmail = requesterEmail;
  }

  public String getResolutionNote() {
    return resolutionNote;
  }

  public void setResolutionNote(String resolutionNote) {
    this.resolutionNote = resolutionNote;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }

  public UUID getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(UUID assignedTo) {
    this.assignedTo = assignedTo;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(UUID resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void setRejectedAt(Instant rejectedAt) {
    this.rejectedAt = rejectedAt;
  }

  public UUID getRejectedBy() {
    return rejectedBy;
  }

  public void setRejectedBy(UUID rejectedBy) {
    this.rejectedBy = rejectedBy;
  }
}
