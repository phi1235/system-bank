package com.banksystem.auth.api.b2b;

import com.banksystem.auth.api.dto.B2bDtos.JwksResponse;
import com.banksystem.auth.api.dto.B2bDtos.OAuth2TokenRequest;
import com.banksystem.auth.api.dto.B2bDtos.OAuth2TokenResponse;
import com.banksystem.auth.application.b2b.B2bOAuth2TokenService;
import com.banksystem.common.security.SecurityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-banking/v1/oauth2")
public class B2bOAuth2Controller {

  private final B2bOAuth2TokenService tokenService;

  public B2bOAuth2Controller(B2bOAuth2TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OAuth2TokenResponse> tokenUrlEncoded(
      @ModelAttribute OAuth2TokenRequest req,
      HttpServletRequest httpRequest) {
    String thumbprint = httpRequest.getHeader(SecurityHeaders.B2B_CERT_THUMBPRINT);
    OAuth2TokenResponse response = tokenService.issueB2bToken(req, thumbprint);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(response);
  }

  @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OAuth2TokenResponse> tokenJson(
      @RequestBody OAuth2TokenRequest req,
      HttpServletRequest httpRequest) {
    String thumbprint = httpRequest.getHeader(SecurityHeaders.B2B_CERT_THUMBPRINT);
    OAuth2TokenResponse response = tokenService.issueB2bToken(req, thumbprint);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(response);
  }

  @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JwksResponse> jwks() {
    return ResponseEntity.ok(tokenService.getJwks());
  }
}
