# ADR-006 — Lazy customer profile

## Status

Accepted

## Decision

Register only creates auth user. Profile via `POST /customers/me` after login.
