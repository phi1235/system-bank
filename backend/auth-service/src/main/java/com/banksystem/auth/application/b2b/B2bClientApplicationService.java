package com.banksystem.auth.application.b2b;

import com.banksystem.auth.api.dto.B2bDtos.B2bClientCreateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientResponse;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientUpdateRequest;
import com.banksystem.auth.application.b2b.query.B2bClientSearchQuery;
import java.util.List;
import org.springframework.data.domain.Page;

public interface B2bClientApplicationService {

  List<B2bClientResponse> listClients(String status, String q);

  Page<B2bClientResponse> listClients(B2bClientSearchQuery query);

  B2bClientResponse getClient(String clientId);

  B2bClientResponse createClient(B2bClientCreateRequest request);

  B2bClientResponse updateClient(String clientId, B2bClientUpdateRequest request);

  void deleteClient(String clientId);
}
