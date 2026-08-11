package com.banksystem.auth.api.auth;

import com.banksystem.auth.api.dto.AuthDtos.InternalUserCountsResponse;
import com.banksystem.auth.application.auth.InternalUserQueryService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequireInternalApiKey
public class InternalUserController {

  private final InternalUserQueryService queryService;

  public InternalUserController(InternalUserQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/counts")
  public ApiResponse<InternalUserCountsResponse> counts() {
    return ApiResponse.ok(queryService.counts());
  }
}
