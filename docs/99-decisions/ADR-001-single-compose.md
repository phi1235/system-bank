# ADR-001 — Single docker-compose

## Status

Accepted

## Context

Draft có `backend/docker-compose.yml` và `infra/docker-compose.yml` → drift, AI confuse.

## Decision

Chỉ duy trì **`infra/docker-compose.yml`** cho toàn stack.

## Consequences

Backend modules chỉ có Dockerfile; orchestration tập trung infra.
