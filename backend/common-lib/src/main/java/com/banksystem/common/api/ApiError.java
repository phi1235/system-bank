package com.banksystem.common.api;

import java.util.List;

public record ApiError(
    String code,
    String message,
    List<String> details
) {
  public ApiError(String code, String message) {
    this(code, message, List.of());
  }
}
