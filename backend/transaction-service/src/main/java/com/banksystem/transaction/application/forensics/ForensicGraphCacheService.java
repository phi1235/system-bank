package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicDtos.CausalGraphResponse;
import com.banksystem.transaction.domain.forensics.ForensicGraphCacheEntity;
import com.banksystem.transaction.domain.forensics.ForensicGraphCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class ForensicGraphCacheService {
  private static final long GRAPH_VERSION = 1L;
  private final ForensicGraphCacheRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration ttl;
  private final ForensicTelemetry telemetry;

  public ForensicGraphCacheService(
      ForensicGraphCacheRepository repository,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${bank.forensics.graph-cache-ttl}") Duration ttl,
      ForensicTelemetry telemetry) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.ttl = ttl;
    this.telemetry = telemetry;
  }

  @Transactional(readOnly = true)
  public CausalGraphResponse find(UUID transactionId, Instant watermark) {
    Instant now = clock.instant();
    CausalGraphResponse graph = repository.findById(transactionId)
        .filter(cache -> cache.isFresh(GRAPH_VERSION, watermark, now))
        .map(this::decode)
        .orElse(null);
    telemetry.graphCache(graph != null, graph == null ? "UNKNOWN" : graph.completeness());
    return graph;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CausalGraphResponse store(
      UUID transactionId, Instant watermark, CausalGraphResponse graph) {
    Instant now = clock.instant();
    repository.upsert(transactionId, GRAPH_VERSION, graph.completeness(), watermark,
        encode(graph), now.plus(ttl), now);
    return graph;
  }

  private String encode(CausalGraphResponse graph) {
    try { return objectMapper.writeValueAsString(graph); }
    catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cannot serialize causal graph cache", exception);
    }
  }

  private CausalGraphResponse decode(ForensicGraphCacheEntity cache) {
    try { return objectMapper.readValue(cache.getGraphJson(), CausalGraphResponse.class); }
    catch (JsonProcessingException exception) { return null; }
  }
}
