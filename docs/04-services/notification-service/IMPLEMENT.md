# IMPLEMENT — notification-service

## Goal

Consume Kafka transfer events → mock email/SMS + idempotent processed_events.

## Path

`backend/notification-service/`

## Read first

- contracts/notification-service.md
- er-diagrams/notification-service.md
- communication.md (topics)

## Checklist

- [x] Flyway notification_logs + processed_events
- [x] Kafka listeners for completed + failed
- [x] Idempotent handle by eventId
- [x] MockEmailSender: log INFO structured + save body to DB
- [x] MockSmsSender: log only
- [x] Optional GET debug endpoint internal (`GET /internal/notifications`)
- [x] Eureka register (`NOTIFICATION-SERVICE`)
- [x] Docker host `18085:8085`

## Payload mapping

Use email from event if present; else `user-{userId}@bank.local` mock.

## Done when

After successful transfer, notification_logs has 1 SENT row; redeliver Kafka does not duplicate. ✅ (2026-07-09)
