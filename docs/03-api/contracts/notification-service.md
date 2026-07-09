# Contract — notification-service

No public business API required for MVP.

## Optional admin/debug

### GET /api/v1/notifications/me  (JWT)

List my notification logs (if recipient matched email from event — may need user email in event payload)

**MVP:** include `recipientEmail` optional in event; or only internal log viewer:

### GET /internal/notifications?eventId=  (internal API key)

Header: `X-Internal-Api-Key: <INTERNAL_API_KEY>`

Returns last 50 (or filter by `eventId`) notification log rows: channel, recipient, template, status, body.

Host direct: `http://localhost:18085/internal/notifications`

Also: `docker logs bank-notification | grep MOCK_EMAIL`

## Kafka consume

Topics: `bank.transaction.completed`, `bank.transaction.failed`  
See ER notification + communication docs.
