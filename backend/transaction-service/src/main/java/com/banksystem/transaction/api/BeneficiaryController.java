package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.CreateBeneficiaryRequest;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.UpdateBeneficiaryRequest;
import com.banksystem.transaction.application.BeneficiaryService;
import com.banksystem.transaction.config.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer beneficiary book for internal transfers.
 * Thin controller: auth + DTO validation + service call only.
 */
@RestController
@RequestMapping("/api/v1/transactions/beneficiaries")
public class BeneficiaryController {

  private final BeneficiaryService service;

  public BeneficiaryController(BeneficiaryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<BeneficiaryResponse>> list() {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId()));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<BeneficiaryResponse>> create(
      @Valid @RequestBody CreateBeneficiaryRequest req) {
    var user = UserContext.requireUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.create(user.userId(), req)));
  }

  @PutMapping("/{id}")
  public ApiResponse<BeneficiaryResponse> rename(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBeneficiaryRequest req) {
    return ApiResponse.ok(service.rename(UserContext.requireUser().userId(), id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    service.deactivate(UserContext.requireUser().userId(), id);
    return ResponseEntity.noContent().build();
  }
}
