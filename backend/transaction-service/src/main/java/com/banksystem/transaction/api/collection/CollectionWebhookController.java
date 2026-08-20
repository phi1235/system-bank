package com.banksystem.transaction.api.collection;

import com.banksystem.transaction.api.dto.CollectionDtos.WebhookProcessingResult;
import com.banksystem.transaction.application.collection.InboundWebhookService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/callbacks/collections")
public class CollectionWebhookController {

  private final InboundWebhookService inboundWebhookService;

  public CollectionWebhookController(InboundWebhookService inboundWebhookService) {
    this.inboundWebhookService = inboundWebhookService;
  }

  @PostMapping("/{provider}")
  public ResponseEntity<WebhookProcessingResult> handleWebhook(
      @PathVariable String provider,
      @RequestBody String rawPayload,
      @RequestHeader Map<String, String> headers) {
    WebhookProcessingResult result = inboundWebhookService.processInboundWebhook(provider, rawPayload, headers);
    if (!result.success() && "INVALID_PAYLOAD".equals(result.status())) {
      return ResponseEntity.badRequest().body(result);
    }
    return ResponseEntity.ok(result);
  }
}
