package com.banksystem.corporate.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.VerifyTotpReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.VerifyTotpRes;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "AUTH-SERVICE", url = "${bank.feign.auth-url:}")
public interface AuthClient {

  @PostMapping("/internal/users/{userId}/verify-totp")
  ApiResponse<VerifyTotpRes> verifyTotp(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("userId") UUID userId,
      @RequestBody VerifyTotpReq req);
}
