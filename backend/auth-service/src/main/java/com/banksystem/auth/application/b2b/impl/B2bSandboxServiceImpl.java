package com.banksystem.auth.application.b2b.impl;

import com.banksystem.auth.api.dto.B2bDtos.B2bSandboxExecuteRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bSandboxExecuteResponse;
import com.banksystem.auth.application.b2b.B2bSandboxService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class B2bSandboxServiceImpl implements B2bSandboxService {

  @Override
  public B2bSandboxExecuteResponse executeSimulation(B2bSandboxExecuteRequest req) {
    long start = System.currentTimeMillis();
    String msgId = "SANDBOX-MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    String nowIso = Instant.now().toString();

    String responseJson;
    if ("camt.053".equalsIgnoreCase(req.messageType())) {
      responseJson = """
          {
            "groupHeader": { "messageIdentification": "%s", "creationDateTime": "%s" },
            "statement": [ { "statementIdentification": "STMT-DEMO-001", "account": { "accountNumber": "10987654321", "currency": "VND" }, "balance": [ { "type": "CLBD", "amount": { "currency": "VND", "value": 500000000.00 } } ] } ]
          }
          """.formatted(msgId, nowIso).trim();
    } else {
      responseJson = """
          {
            "groupHeader": { "messageIdentification": "PSR-%s", "originalMessageIdentification": "%s" },
            "originalPaymentInformationAndStatus": [ { "paymentInformationStatus": "ACTC", "transactionInformationAndStatus": [ { "transactionStatus": "ACSC", "statusReasonInformation": { "reason": { "code": "G000" }, "additionalInformation": "Transaction successfully verified and simulated via ISO 20022 Sandbox Engine." } } ] } ]
          }
          """.formatted(msgId, msgId).trim();
    }

    long elapsed = System.currentTimeMillis() - start;

    return new B2bSandboxExecuteResponse(
        msgId,
        "SUCCESS",
        responseJson,
        Math.max(elapsed, 18),
        true
    );
  }
}
