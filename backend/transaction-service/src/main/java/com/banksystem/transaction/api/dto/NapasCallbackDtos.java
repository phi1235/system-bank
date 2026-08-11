package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class NapasCallbackDtos {

  private NapasCallbackDtos() {}

  public record PaymentCallbackRequest(
      String eventId,
      String clientRequestId,
      String napasRefId,
      @NotBlank String status,
      String responseCode,
      String responseMessage) {

    public String signingPayload(long timestamp) {
      return timestamp + "|" + value(eventId) + "|" + value(clientRequestId) + "|"
          + value(napasRefId) + "|" + status.trim().toUpperCase() + "|"
          + value(responseCode);
    }

    private static String value(String input) {
      return input == null ? "" : input.trim();
    }
  }
}
