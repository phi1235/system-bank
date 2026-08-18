package com.banksystem.transaction.application.forensics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ForensicEvidenceSanitizer {
  private static final Set<String> MASKED_FIELDS = Set.of(
      "toAccountNumber", "targetAccountName", "description", "detail", "metadata");
  private final ForensicArtifactCodec codec;

  public ForensicEvidenceSanitizer(ForensicArtifactCodec codec) { this.codec = codec; }

  public JsonNode sanitize(Object source) {
    JsonNode copy = codec.tree(source).deepCopy();
    sanitizeNode(copy);
    return copy;
  }

  private void sanitizeNode(JsonNode node) {
    if (node instanceof ObjectNode object) {
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (MASKED_FIELDS.contains(field.getKey()) && !field.getValue().isNull()) {
          object.put(field.getKey(), token(field.getValue().asText()));
        } else {
          sanitizeNode(field.getValue());
        }
      }
    } else if (node instanceof ArrayNode array) {
      array.forEach(this::sanitizeNode);
    }
  }

  private String token(String value) {
    return "REDACTED:" + codec.sha256(value).substring(0, 12);
  }
}
