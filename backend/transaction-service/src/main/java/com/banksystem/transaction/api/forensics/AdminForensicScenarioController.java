package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicScenarioDtos.ConfirmScenarioRequest;
import com.banksystem.transaction.api.dto.ForensicScenarioDtos.CreateScenarioRequest;
import com.banksystem.transaction.api.dto.ForensicScenarioDtos.ScenarioResponse;
import com.banksystem.transaction.application.forensics.ForensicScenarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/scenarios")
public class AdminForensicScenarioController {
  private final ForensicScenarioService service;
  public AdminForensicScenarioController(ForensicScenarioService service) { this.service = service; }

  @GetMapping
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_REPLAY_EXECUTE)
  public ApiResponse<List<ScenarioResponse>> confirmed() { return ApiResponse.ok(service.confirmed()); }

  @GetMapping("/all")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<List<ScenarioResponse>> all() { return ApiResponse.ok(service.all()); }

  @GetMapping("/engines")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<List<String>> engines() { return ApiResponse.ok(service.engines()); }

  @GetMapping("/fault-types")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<List<String>> faultTypes() {
    return ApiResponse.ok(service.faultTypes());
  }

  @PostMapping
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<ScenarioResponse> create(@Valid @RequestBody CreateScenarioRequest request) {
    return ApiResponse.ok(service.create(request, UserContext.requireUser().userId()));
  }

  @PostMapping("/{id}/confirm")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<ScenarioResponse> confirm(
      @PathVariable String id, @Valid @RequestBody ConfirmScenarioRequest request) {
    return ApiResponse.ok(service.confirm(id, request.expectedVersion(), UserContext.requireUser().userId()));
  }
}
