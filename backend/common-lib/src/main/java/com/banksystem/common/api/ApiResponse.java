package com.banksystem.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Meta meta
) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, Meta.now());
  }

  public static <T> ApiResponse<T> ok(T data, String correlationId) {
    return new ApiResponse<>(true, data, null, Meta.now(correlationId));
  }

  public static <T> ApiResponse<T> fail(ApiError error) {
    return new ApiResponse<>(false, null, error, Meta.now());
  }

  public static <T> ApiResponse<T> fail(ApiError error, String correlationId) {
    return new ApiResponse<>(false, null, error, Meta.now(correlationId));
  }
}
