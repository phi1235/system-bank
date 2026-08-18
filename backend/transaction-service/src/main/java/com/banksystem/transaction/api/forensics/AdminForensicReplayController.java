package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.CreateReplayRequest;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.CreateTwinForkRequest;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.ReplayRunResponse;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.TwinForkResponse;
import com.banksystem.transaction.application.forensics.ForensicArtifactStorage;
import com.banksystem.transaction.application.forensics.ForensicReplayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/twin")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_REPLAY_EXECUTE)
public class AdminForensicReplayController {
  private final ForensicReplayService service;

  public AdminForensicReplayController(ForensicReplayService service) { this.service = service; }

  @PostMapping("/forks")
  public ApiResponse<TwinForkResponse> createFork(
      @Valid @RequestBody CreateTwinForkRequest request) {
    return ApiResponse.ok(service.createFork(
        request.transactionId(), request.ttlMinutes(), UserContext.requireUser()));
  }

  @PostMapping("/replays")
  public ApiResponse<ReplayRunResponse> createReplay(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateReplayRequest request) {
    return ApiResponse.ok(service.createReplay(idempotencyKey, request, UserContext.requireUser()));
  }

  @GetMapping("/runs/{runId}")
  public ApiResponse<ReplayRunResponse> run(@PathVariable UUID runId) {
    return ApiResponse.ok(service.getRun(runId, UserContext.requireUser()));
  }

  @GetMapping("/runs/{runId}/result")
  public ResponseEntity<byte[]> result(@PathVariable UUID runId) {
    ForensicArtifactStorage.StoredArtifact artifact = service.result(runId, UserContext.requireUser());
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(artifact.content());
  }

  @DeleteMapping("/forks/{forkId}")
  public ApiResponse<Void> deleteFork(@PathVariable UUID forkId) {
    service.deleteFork(forkId, UserContext.requireUser());
    return ApiResponse.ok(null);
  }
}
