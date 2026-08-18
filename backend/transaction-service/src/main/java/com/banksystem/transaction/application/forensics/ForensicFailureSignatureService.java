package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicDtos.CausalEdgeResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.CausalNodeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Produces a stable failure-family identifier without transaction-specific IDs or timestamps. */
@Component
class ForensicFailureSignatureService {
  private static final Pattern UUID_PATTERN = Pattern.compile(
      "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

  String create(List<CausalNodeResponse> nodes, List<CausalEdgeResponse> edges) {
    List<CausalNodeResponse> anomalousNodes = nodes.stream()
        .filter(CausalNodeResponse::anomalous)
        .toList();
    if (anomalousNodes.isEmpty()) {
      return null;
    }
    Map<String, CausalNodeResponse> nodesById = nodes.stream()
        .collect(Collectors.toMap(CausalNodeResponse::id, Function.identity()));
    String nodeShape = anomalousNodes.stream()
        .map(this::nodeShape)
        .sorted()
        .collect(Collectors.joining(","));
    String edgeShape = edges.stream()
        .filter(edge -> isAnomalousEndpoint(edge, nodesById))
        .map(edge -> edgeShape(edge, nodesById))
        .sorted()
        .collect(Collectors.joining(","));
    return "fsig-v1:" + sha256(nodeShape + "|" + edgeShape);
  }

  private boolean isAnomalousEndpoint(
      CausalEdgeResponse edge, Map<String, CausalNodeResponse> nodesById) {
    CausalNodeResponse from = nodesById.get(edge.fromNodeId());
    CausalNodeResponse to = nodesById.get(edge.toNodeId());
    return from != null && to != null && (from.anomalous() || to.anomalous());
  }

  private String edgeShape(
      CausalEdgeResponse edge, Map<String, CausalNodeResponse> nodesById) {
    return nodesById.get(edge.fromNodeId()).type() + ">" + edge.relation() + ">"
        + nodesById.get(edge.toNodeId()).type();
  }

  private String nodeShape(CausalNodeResponse node) {
    return normalize(node.type()) + ":" + normalize(node.label()) + ":"
        + normalize(node.status());
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "NONE";
    }
    return UUID_PATTERN.matcher(value).replaceAll("{UUID}")
        .trim().toUpperCase(Locale.ROOT);
  }

  private String sha256(String canonical) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
