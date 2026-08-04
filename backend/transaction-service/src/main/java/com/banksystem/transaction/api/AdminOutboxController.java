package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.OutboxDtos.AdminOutboxFilterRequest;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxCountsResponse;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxEventResponse;
import com.banksystem.transaction.application.OutboxAdminService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff outbox ops. HTTP + permission only; rules in {@link OutboxAdminService}.
 * Gateway: {@code /api/v1/admin/**} → TRANSACTION-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequirePermission("transactions:list:view")
public class AdminOutboxController {

  private final OutboxAdminService service;

  public AdminOutboxController(OutboxAdminService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResponse<OutboxEventResponse>> list(@Valid @ModelAttribute AdminOutboxFilterRequest req) {
    return ApiResponse.ok(service.list(req));
  }

  @PostMapping("/search")
  public ApiResponse<PageResponse<OutboxEventResponse>> search(@Valid @RequestBody AdminOutboxFilterRequest req) {
    return ApiResponse.ok(service.list(req));
  }

  @GetMapping("/counts")
  public ApiResponse<OutboxCountsResponse> counts() {
    return ApiResponse.ok(service.counts());
  }

  @GetMapping("/{id}")
  public ApiResponse<OutboxEventResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  /** Re-queue a DEAD event for another publish cycle. */
  @PostMapping("/{id}/replay")
  public ApiResponse<OutboxEventResponse> replay(@PathVariable UUID id) {
    return ApiResponse.ok(service.replay(id));
  }
}
