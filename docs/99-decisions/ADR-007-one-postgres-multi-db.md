# ADR-007 — One Postgres, multiple databases

## Status

Accepted

## Decision

Single Postgres container; databases `bank_*` via init script. Still DB-per-service logically.
