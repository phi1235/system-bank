package com.banksystem.auth.api;

import com.banksystem.auth.api.dto.PasswordResetDtos.ChangePasswordRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.CreateTicketRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.FulfillResponse;
import com.banksystem.auth.api.dto.PasswordResetDtos.LockRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.RejectRequest;
import com.banksystem.auth.api.dto.PasswordResetDtos.TicketResponse;
import com.banksystem.auth.application.PasswordResetService;
import com.banksystem.auth.application.permission.PermissionChecker;
import com.banksystem.auth.config.UserPrincipal;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  public PasswordResetController(PasswordResetService passwordResetService) {
    this.passwordResetService = passwordResetService;
  }

  /** Guest / customer: open a reset ticket */
  @PostMapping("/auth/password-reset/tickets")
  public ApiResponse<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest body) {
    return ApiResponse.ok(passwordResetService.createTicket(body));
  }

  /** Authenticated user: change password (also clears mustChangePassword) */
  @PostMapping("/auth/password/change")
  public ApiResponse<Map<String, String>> changePassword(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest body) {
    passwordResetService.changePassword(principal.userId(), body);
    return ApiResponse.ok(Map.of("status", "ok"));
  }

  @GetMapping("/admin/password-reset/tickets")
  public ApiResponse<PageResponse<TicketResponse>> listTickets(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PermissionChecker.requirePasswordReset(principal);
    return ApiResponse.ok(passwordResetService.listTickets(status, page, size));
  }

  @PostMapping("/admin/password-reset/tickets/findTicketByCondition")
  public ApiResponse<PageResponse<TicketResponse>> findTicketByCondition(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TicketSearchRequest req) {
    PermissionChecker.requirePasswordReset(principal);
    return ApiResponse.ok(passwordResetService.listTickets(req.status(), req.page(), req.size()));
  }

  public record TicketSearchRequest(String status, Integer page, Integer size) {}

  /** Direct fulfill — no second checker; password never in response */
  @PostMapping("/admin/password-reset/tickets/{id}/fulfill")
  public ApiResponse<FulfillResponse> fulfill(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id) {
    PermissionChecker.requirePasswordReset(principal);
    return ApiResponse.ok(passwordResetService.fulfill(id, principal.userId()));
  }

  @PostMapping("/admin/password-reset/tickets/{id}/reject")
  public ApiResponse<TicketResponse> reject(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @RequestBody(required = false) RejectRequest body) {
    PermissionChecker.requirePasswordReset(principal);
    return ApiResponse.ok(passwordResetService.reject(id, principal.userId(), body));
  }

  /** Direct blind reset from user-management row (no separate ticket UI). */
  @PostMapping("/admin/users/{userId}/password-reset")
  public ApiResponse<FulfillResponse> resetByUser(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID userId,
      @RequestParam(required = false, defaultValue = "EMAIL") String channel) {
    PermissionChecker.requirePasswordReset(principal);
    return ApiResponse.ok(passwordResetService.resetByUserId(userId, principal.userId(), channel));
  }

  @PostMapping("/admin/users/{userId}/lock")
  public ApiResponse<Map<String, String>> lock(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID userId,
      @RequestBody(required = false) LockRequest body) {
    PermissionChecker.requireUserLock(principal);
    passwordResetService.lockUser(userId, principal.userId(), body);
    return ApiResponse.ok(Map.of("status", "LOCKED"));
  }

  @PostMapping("/admin/users/{userId}/unlock")
  public ApiResponse<Map<String, String>> unlock(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID userId) {
    PermissionChecker.requireUserLock(principal);
    passwordResetService.unlockUser(userId, principal.userId());
    return ApiResponse.ok(Map.of("status", "UNLOCKED"));
  }
}
