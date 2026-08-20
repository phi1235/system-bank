package com.banksystem.auth.application.b2b;

import com.banksystem.auth.api.dto.B2bDtos.B2bSandboxExecuteRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bSandboxExecuteResponse;

public interface B2bSandboxService {

  B2bSandboxExecuteResponse executeSimulation(B2bSandboxExecuteRequest req);
}
