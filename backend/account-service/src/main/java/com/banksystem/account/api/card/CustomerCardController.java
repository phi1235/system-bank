package com.banksystem.account.api.card;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.api.dto.CardDtos.CardRevealResponse;
import com.banksystem.account.api.dto.CardDtos.UpdateCardLimitRequest;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer virtual debit cards. HTTP only; rules in {@link CardService}.
 * Gateway: {@code /api/v1/cards/**} and {@code /api/v1/accounts/**} → ACCOUNT-SERVICE.
 */
@RestController
@RequestMapping("/api/v1")
public class CustomerCardController {

  private final CardService service;

  public CustomerCardController(CardService service) {
    this.service = service;
  }

  /** Creates an approval request (REQUESTED) — the card is issued only after staff approval. */
  @PostMapping("/accounts/{accountId}/cards")
  public ApiResponse<CardResponse> request(@PathVariable UUID accountId) {
    return ApiResponse.ok(service.request(accountId, UserContext.requireUser()));
  }

  @GetMapping("/cards")
  public ApiResponse<List<CardResponse>> mine() {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId()));
  }

  @PostMapping("/cards/{id}/activate")
  public ApiResponse<CardResponse> activate(@PathVariable UUID id) {
    return ApiResponse.ok(service.activate(id, UserContext.requireUser()));
  }

  @PostMapping("/cards/{id}/lock")
  public ApiResponse<CardResponse> lock(@PathVariable UUID id) {
    return ApiResponse.ok(service.lock(id, UserContext.requireUser()));
  }

  @PostMapping("/cards/{id}/unlock")
  public ApiResponse<CardResponse> unlock(@PathVariable UUID id) {
    return ApiResponse.ok(service.unlock(id, UserContext.requireUser()));
  }

  @PostMapping("/cards/{id}/close")
  public ApiResponse<CardResponse> close(@PathVariable UUID id) {
    return ApiResponse.ok(service.close(id, UserContext.requireUser()));
  }

  @PatchMapping("/cards/{id}/limits")
  public ApiResponse<CardResponse> updateLimit(
      @PathVariable UUID id, @Valid @RequestBody UpdateCardLimitRequest request) {
    return ApiResponse.ok(service.updateLimit(id, request, UserContext.requireUser()));
  }

  /** Owner-only full PAN (virtual card must be readable to be used). */
  @PostMapping("/cards/{id}/reveal")
  public ApiResponse<CardRevealResponse> reveal(@PathVariable UUID id) {
    return ApiResponse.ok(service.reveal(id, UserContext.requireUser()));
  }
}
