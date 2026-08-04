package com.banksystem.auth.domain.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tickets")
public class PasswordResetTicketEntity {

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 50)
  private String username;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false, length = 20)
  private String channel = "EMAIL";

  @Column(nullable = false, length = 20)
  private String status = "OPEN";

  @Column(name = "requester_note")
  private String requesterNote;

  @Column(name = "reject_reason")
  private String rejectReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "fulfilled_at")
  private Instant fulfilledAt;

  @Column(name = "fulfilled_by")
  private UUID fulfilledBy;

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

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRequesterNote() {
    return requesterNote;
  }

  public void setRequesterNote(String requesterNote) {
    this.requesterNote = requesterNote;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getFulfilledAt() {
    return fulfilledAt;
  }

  public void setFulfilledAt(Instant fulfilledAt) {
    this.fulfilledAt = fulfilledAt;
  }

  public UUID getFulfilledBy() {
    return fulfilledBy;
  }

  public void setFulfilledBy(UUID fulfilledBy) {
    this.fulfilledBy = fulfilledBy;
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
