# Observability

## Tracing (Zipkin)

- Each service: `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`
- Sampling: `management.tracing.sampling.probability` (default `1.0` for demo)
- Endpoint: `management.zipkin.tracing.endpoint` ← env `ZIPKIN_ENDPOINT`
- Compose: `ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans`
- UI: http://localhost:9411

Feign/HTTP spans propagate via Micrometer Observation (Boot 3).

### Demo

1. Perform a transfer (UI or curl)
2. Open Zipkin → service name e.g. `TRANSACTION-SERVICE` / `api-gateway`
3. Find span for HTTP transfer + Feign debit/credit

## Metrics (Prometheus)

- Actuator: `/actuator/prometheus` exposed on every service
- Registry: `micrometer-registry-prometheus`
- Scrape file: `infra/prometheus/prometheus.yml`
- UI: http://localhost:9090 → Status → Targets

## Grafana

- http://localhost:3000 (default admin/admin)
- Provisioned Prometheus datasource + dashboard *Bank System — JVM / HTTP overview*
- Path: `infra/grafana/provisioning/`

## Logging

Pattern includes `traceId` / `spanId`:

```
%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Console only (no ELK in MVP). Docker json-file driver default.

## Interview talking points

1. Show Zipkin one transfer end-to-end  
2. Show Prometheus `http_server_requests` / JVM heap  
3. Rate-limit + audit log as ops controls  
