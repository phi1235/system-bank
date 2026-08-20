package com.banksystem.auth.application.b2b;

import com.banksystem.auth.api.dto.B2bDtos.OAuth2TokenRequest;
import com.banksystem.auth.api.dto.B2bDtos.OAuth2TokenResponse;
import com.banksystem.auth.api.dto.B2bDtos.JwksResponse;

public interface B2bOAuth2TokenService {

  OAuth2TokenResponse issueB2bToken(OAuth2TokenRequest request, String certThumbprintFromHeader);

  JwksResponse getJwks();
}
