# ADR-004 — customerId == userId

## Status

Accepted

## Decision

1:1 mapping; `customers.id = auth users.id`.

## Consequences

Simpler ownership checks; no mapping table.
