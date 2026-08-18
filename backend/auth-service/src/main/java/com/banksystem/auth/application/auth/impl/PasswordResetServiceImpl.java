package com.banksystem.auth.application.auth.impl;
import com.banksystem.auth.api.dto.PasswordResetDtos.ChangePasswordRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.CreateTicketRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.FulfillResponse;
import com.banksystem.auth.api.dto.PasswordResetDtos.LockRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.RejectRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.TicketResponse;
import com.banksystem.auth.application.auth.PasswordResetService;
import com.banksystem.auth.application.auth.SessionService;
import com.banksystem.auth.domain.auth.AuthAuditLogEntity;
import com.banksystem.auth.domain.auth.AuthAuditLogRepository;
import com.banksystem.auth.domain.auth.PasswordResetTicketEntity;
import com.banksystem.auth.domain.auth.PasswordResetTicketRepository;
import com.banksystem.auth.domain.auth.UserEntity;
import com.banksystem.auth.domain.auth.UserRepository;
import com.banksystem.auth.infrastructure.security.BoundPasswordEncoder;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Blind password reset: temporary password is generated server-side, hashed, delivered via
 * mock email/SMS log. Admin API never returns the plaintext password.
 *
 * <p>MVP flow is <b>direct</b> for staff with {@code users:password:reset} (no checker-maker):
 * identity is assumed verified when accepting the ticket; action is fully audited.
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final PasswordResetTicketRepository ticketRepository;
  private final AuthAuditLogRepository auditLogRepository;
  private final BoundPasswordEncoder passwordEncoder;
  private final SessionService sessionService;

  @Value("${bank.notification.internal-url}")
  private String notificationServiceUrl;

  @Value("${bank.notification.internal-api-key}")
  private String notificationApiKey;

  public PasswordResetServiceImpl(
      UserRepository userRepository,
      PasswordResetTicketRepository ticketRepository,
      AuthAuditLogRepository auditLogRepository,
      BoundPasswordEncoder passwordEncoder,
      SessionService sessionService) {
    this.userRepository = userRepository;
    this.ticketRepository = ticketRepository;
    this.auditLogRepository = auditLogRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionService = sessionService;
  }

  @Transactional
  public TicketResponse createTicket(CreateTicketRequest req) {
    String key = req.usernameOrEmail() == null ? "" : req.usernameOrEmail().trim();
    if (key.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Username or email required");
    }
    UserEntity user = userRepository.findByUsername(BoundPasswordEncoder.normalizeUsername(key))
        .or(() -> userRepository.findByEmailIgnoreCase(key))
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
            "If the account exists, a ticket will be processed by support."));

    // Avoid user enumeration in real prod — here explicit for MVP ops clarity
    if (ticketRepository.existsByUserIdAndStatus(user.getId(), "OPEN")) {
      throw new BusinessException("TICKET_EXISTS", "An open reset ticket already exists");
    }

    String channel = req.channel() == null || req.channel().isBlank()
        ? "EMAIL"
        : req.channel().trim().toUpperCase(Locale.ROOT);
    if (!channel.equals("EMAIL") && !channel.equals("SMS")) {
      channel = "EMAIL";
    }

    PasswordResetTicketEntity t = new PasswordResetTicketEntity();
    t.setId(UUID.randomUUID());
    t.setUserId(user.getId());
    t.setUsername(user.getUsername());
    t.setEmail(user.getEmail());
    t.setChannel(channel);
    t.setStatus("OPEN");
    t.setRequesterNote(req.note());
    t.setCreatedAt(Instant.now());
    ticketRepository.save(t);
    audit(user.getId(), "PWD_RESET_TICKET_OPEN", null, "ticketId=" + t.getId());
    return toTicket(t);
  }

  @Transactional(readOnly = true)
  public PageResponse<TicketResponse> listTickets(String status, Integer page, Integer size) {
    int pg = page != null ? page : 0;
    int sz = size != null ? Math.min(Math.max(size, 1), 100) : 20;
    PageRequest pr = PageRequest.of(Math.max(pg, 0), sz);
    Page<PasswordResetTicketEntity> p;
    if (status != null && !status.isBlank()) {
      p = ticketRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT), pr);
    } else {
      p = ticketRepository.findAllByOrderByCreatedAtDesc(pr);
    }
    List<TicketResponse> items = p.getContent().stream().map(this::toTicket).toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  /**
   * Direct fulfill (no second checker): generates temp password, stores hash only,
   * "sends" via mock channel. Response never contains plaintext password.
   */
  @Transactional
  public FulfillResponse fulfill(UUID ticketId, UUID adminId) {
    PasswordResetTicketEntity t = ticketRepository.findById(ticketId)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
    if (!"OPEN".equals(t.getStatus())) {
      throw new BusinessException("TICKET_NOT_OPEN", "Ticket is not OPEN");
    }
    UserEntity user = userRepository.findById(t.getUserId())
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    return issueTempPassword(user, adminId, t.getChannel(), t);
  }

  /**
   * Admin action from user list: blind reset without requiring a prior ticket.
   * Closes any OPEN tickets for the user.
   */
  @Transactional
  public FulfillResponse resetByUserId(UUID userId, UUID adminId, String channel) {
    if (userId.equals(adminId)) {
      throw new BusinessException("CANNOT_RESET_SELF",
          "You cannot reset your own password this way");
    }
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    String ch = channel == null || channel.isBlank() ? "EMAIL" : channel.trim().toUpperCase(Locale.ROOT);
    if (!ch.equals("EMAIL") && !ch.equals("SMS")) {
      ch = "EMAIL";
    }
    PasswordResetTicketEntity open = ticketRepository.findByUserIdAndStatus(userId, "OPEN").stream()
        .findFirst()
        .orElse(null);
    return issueTempPassword(user, adminId, ch, open);
  }

  private FulfillResponse issueTempPassword(
      UserEntity user, UUID adminId, String channel, PasswordResetTicketEntity ticketOrNull) {
    String tempPassword = generateTempPassword();
    user.setPasswordHash(passwordEncoder.encode(tempPassword, user.getUsername()));
    user.setMustChangePassword(true);
    // Unlock if locked — ops chose to restore access via reset
    user.setEnabled(true);
    user.setLockedReason(null);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    // Force re-login everywhere after admin-issued password
    sessionService.revokeAll(user.getId());

    String ticketId = null;
    if (ticketOrNull != null) {
      ticketOrNull.setStatus("FULFILLED");
      ticketOrNull.setFulfilledAt(Instant.now());
      ticketOrNull.setFulfilledBy(adminId);
      ticketRepository.save(ticketOrNull);
      ticketId = ticketOrNull.getId().toString();
    } else {
      // close any other open tickets defensively
      for (PasswordResetTicketEntity t : ticketRepository.findByUserIdAndStatus(user.getId(), "OPEN")) {
        t.setStatus("FULFILLED");
        t.setFulfilledAt(Instant.now());
        t.setFulfilledBy(adminId);
        ticketRepository.save(t);
        ticketId = t.getId().toString();
      }
    }

    String delivery = "EMAIL".equals(channel) ? user.getEmail() : maskPhonePlaceholder(user.getEmail());
    if (delivery == null || delivery.isBlank()) {
      delivery = user.getUsername() + "@bank.local";
    }
    log.info("MOCK_{} password-reset to={} username={} tempPassword={} (DEV only — not returned to admin UI)",
        channel, delivery, user.getUsername(), tempPassword);
    
    postNotificationAsync(channel, delivery, tempPassword, user);

    audit(adminId, "PWD_RESET_FULFILLED", null,
        "ticketId=" + ticketId + ",targetUser=" + user.getUsername() + ",channel=" + channel);
    audit(user.getId(), "PWD_RESET_TEMP_ISSUED", null, "channel=" + channel);

    return new FulfillResponse(
        ticketId,
        "FULFILLED",
        channel,
        maskEmail(user.getEmail()),
        "Temporary password sent to " + maskEmail(user.getEmail()) + ". Admin cannot view the password."
    );
  }

  @Transactional
  public TicketResponse reject(UUID ticketId, UUID adminId, RejectRequest req) {
    PasswordResetTicketEntity t = ticketRepository.findById(ticketId)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
    if (!"OPEN".equals(t.getStatus())) {
      throw new BusinessException("TICKET_NOT_OPEN", "Ticket is not OPEN");
    }
    t.setStatus("REJECTED");
    t.setRejectedAt(Instant.now());
    t.setRejectedBy(adminId);
    t.setRejectReason(req == null ? null : req.reason());
    ticketRepository.save(t);
    audit(adminId, "PWD_RESET_REJECTED", null, "ticketId=" + t.getId());
    return toTicket(t);
  }

  @Transactional
  public void lockUser(UUID targetUserId, UUID adminId, LockRequest req) {
    UserEntity user = userRepository.findById(targetUserId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    // Admin / sub-admin cannot lock their own account
    if (targetUserId.equals(adminId)) {
      throw new BusinessException("CANNOT_LOCK_SELF",
          "You cannot lock your own account");
    }
    user.setEnabled(false);
    user.setLockedReason(req == null || req.reason() == null || req.reason().isBlank()
        ? "Locked by admin"
        : req.reason().trim());
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    sessionService.revokeAll(targetUserId);
    audit(adminId, "USER_LOCKED", null, "target=" + user.getUsername() + ",reason=" + user.getLockedReason());
  }

  @Transactional
  public void unlockUser(UUID targetUserId, UUID adminId) {
    UserEntity user = userRepository.findById(targetUserId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    user.setEnabled(true);
    user.setLockedReason(null);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    audit(adminId, "USER_UNLOCKED", null, "target=" + user.getUsername());
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest req) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    if (!passwordEncoder.matches(req.currentPassword(), user.getUsername(), user.getPasswordHash())) {
      throw new BusinessException("INVALID_CREDENTIALS", "Current password is incorrect");
    }
    validatePassword(req.newPassword());
    if (req.newPassword().equals(req.currentPassword())) {
      throw new BusinessException("WEAK_PASSWORD", "New password must differ from current");
    }
    user.setPasswordHash(passwordEncoder.encode(req.newPassword(), user.getUsername()));
    user.setMustChangePassword(false);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    // Invalidate every refresh session so stolen tokens cannot be reused after password change.
    int revoked = sessionService.revokeAll(userId);
    audit(userId, "PASSWORD_CHANGED", null, "forcedOrSelf=true,revokedSessions=" + revoked);
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8
        || !password.matches(".*[A-Za-z].*")
        || !password.matches(".*\\d.*")) {
      throw new BusinessException("WEAK_PASSWORD",
          "Password must be at least 8 characters with letters and numbers");
    }
  }

  private static String generateTempPassword() {
    StringBuilder sb = new StringBuilder(12);
    for (int i = 0; i < 12; i++) {
      sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
    }
    // ensure complexity
    sb.setCharAt(0, 'A');
    sb.setCharAt(1, 'b');
    sb.setCharAt(2, '3');
    sb.setCharAt(3, '!');
    return sb.toString();
  }

  private TicketResponse toTicket(PasswordResetTicketEntity t) {
    return new TicketResponse(
        t.getId().toString(),
        t.getUsername(),
        maskEmail(t.getEmail()),
        t.getChannel(),
        t.getStatus(),
        t.getRequesterNote(),
        t.getRejectReason(),
        t.getCreatedAt(),
        t.getFulfilledAt(),
        t.getRejectedAt()
    );
  }

  static String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return "***";
    }
    String[] parts = email.split("@", 2);
    String local = parts[0];
    String domain = parts[1];
    String maskedLocal = local.length() <= 2
        ? "*".repeat(local.length())
        : local.charAt(0) + "***" + local.charAt(local.length() - 1);
    return maskedLocal + "@" + domain;
  }

  private static String maskPhonePlaceholder(String email) {
    return maskEmail(email);
  }

  private void audit(UUID userId, String action, String ip, String detail) {
    auditLogRepository.save(AuthAuditLogEntity.of(userId, action, ip, detail));
  }

  private void postNotificationAsync(String channel, String delivery, String tempPassword, UserEntity user) {
    String bodyMsg = "Mật khẩu tạm thời mới cấp cho tài khoản " + user.getUsername()
        + " là: " + tempPassword + ". Vui lòng sử dụng mật khẩu này để đăng nhập.";
    String json = String.format(
        "{\"channel\":\"%s\",\"recipient\":\"%s\",\"template\":\"PASSWORD_RESET\",\"status\":\"SENT\",\"body\":\"%s\",\"userId\":\"%s\",\"audience\":\"CUSTOMER\"}",
        channel, delivery, bodyMsg.replace("\"", "\\\""), user.getId());
    String apiKeyValue = (notificationApiKey != null && !notificationApiKey.isBlank())
        ? notificationApiKey : "56ae26d48fde6e69d3a6a09d2483bb4f9a93bcd0d9e15fbdb121be9630c05723";
    String baseUrl = (notificationServiceUrl != null && !notificationServiceUrl.isBlank())
        ? notificationServiceUrl : "http://bank-notification:8085";

    Thread t = new Thread(() -> {
      String[] targets = {
          baseUrl + "/internal/notifications",
          "http://bank-notification:8085/internal/notifications",
          "http://notification-service:8085/internal/notifications"
      };
      for (String url : targets) {
        try {
          HttpClient client = HttpClient.newHttpClient();
          HttpRequest req = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .header("X-Internal-Api-Key", apiKeyValue)
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofSeconds(5))
              .build();
          HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
          if (resp.statusCode() == 200) {
            log.info("Password reset notification log posted to {}", url);
            break;
          } else {
            log.warn("Posting notification to {} returned status {} body {}", url, resp.statusCode(), resp.body());
          }
        } catch (Exception ex) {
          log.warn("Failed posting notification log to {}: {}", url, ex.getMessage());
        }
      }
    });
    t.setDaemon(true);
    t.setName("pwd-reset-notify");
    t.start();
  }
}
