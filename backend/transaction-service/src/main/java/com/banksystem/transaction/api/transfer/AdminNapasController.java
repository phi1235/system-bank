package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.transfer.NapasResolutionService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/napas")
@RequirePermission("transactions:recon:execute")
public class AdminNapasController {

  private final NapasResolutionService resolutionService;
  private final TransferMapper mapper;

  public AdminNapasController(NapasResolutionService resolutionService, TransferMapper mapper) {
    this.resolutionService = resolutionService;
    this.mapper = mapper;
  }

  @PostMapping("/transfers/{id}/inquire")
  public ApiResponse<TransferResponse> inquire(@PathVariable UUID id) {
    return ApiResponse.ok(mapper.toResponse(resolutionService.inquireNow(id)));
  }

  @PostMapping("/transfers/{id}/resume")
  public ApiResponse<TransferResponse> resume(@PathVariable UUID id) {
    return ApiResponse.ok(mapper.toResponse(resolutionService.resumePending(id)));
  }
}
