# Financial Forensics Operations Runbook

## Ownership and access

The module is hosted in `transaction-service`; `account-service` remains the ledger system of record.
The administration page is `/admin/forensics` in Back Office.

| Operator | Permission | Allowed operations |
|---|---|---|
| Investigator | `forensics:view` | Search, timeline, causal graph, temporal state |
| Verification operator | `forensics:verify:execute` | Run deterministic verification |
| Reconciliation reviewer | `forensics:case:review` | Case workflow and violation lifecycle |
| Evidence officer | `forensics:evidence:export` | Request and download redacted exports |
| Replay operator | `forensics:replay:execute` | Create sanitized fork and run confirmed scenario |
| Copilot user | `forensics:copilot:use` | Ask questions against sanitized structured evidence |
| Auditor | `forensics:audit:view` | Read case history and evidence metadata |
| Platform administrator | `forensics:admin` | Scenario draft/confirmation and provider health |

Critical findings and replay scenarios require different maker and checker user IDs. No role bypass is implemented in controllers.

## Required local/environment configuration

Values belong in ignored `application-local.yml`, `infra/.env`, or the deployment secret store. Do not add values to committed `application.yml`.

- Feature and workers: `FORENSICS_ENABLED`, batch size/overlap/lease/cron, retention cron.
- Account boundary: `ACCOUNT_INTERNAL_API_KEY` and account-service URL already used by the internal evidence adapter.
- Storage: MinIO/S3 endpoint, access key, secret key, bucket and artifact retention.
- Replay: deployed commit SHA, execution image SHA, fork quota and replay retention.
- Copilot: enabled flag, provider endpoint, API key, model, connect/read timeouts, session retention and message budget.
- Sanitizer salt must be supplied outside Git and must not be logged or included in an export.

Startup intentionally fails when a required property is missing. This prevents accidental fallback to a shared development key or an unverified replay runtime.

## Normal operating flow

1. Open Back Office → Financial Forensics.
2. Search by transaction/account/business date and open an investigation.
3. Check evidence completeness before drawing a conclusion. `PARTIAL` means at least one durable source is unavailable.
4. Run verification. A repeated request uses an idempotency key and cannot create duplicate findings.
5. A reviewer acknowledges and resolves a finding with a reason and structured evidence. Critical resolution requires a different user.
6. If reproduction is needed, create a sanitized fork, select a confirmed scenario and run against the deployed commit SHA.
7. Use Copilot only as a read-only explanation layer. `RAW_FALLBACK` is expected when the provider is disabled, unavailable, or its claims fail validation.

## Replay scenario governance

- A platform administrator creates a sanitized `DRAFT` from a durable incident/evidence reference.
- The backend validates schema version 1 and the allowlisted fault catalog in `sandbox/scenarios/schema-v1.json`.
- A different platform administrator confirms the unchanged draft using optimistic version control.
- Only `CONFIRMED` scenarios appear to replay operators.
- Identical scenario ID, seed and target commit produce the same active fault set and result.
- Replay never accepts a database URL, credential, arbitrary class, shell command, engine name or network target from the request.

## Health and alert interpretation

Monitor these Micrometer series through the existing Prometheus endpoint:

- Verification count, outcome, mode and duration.
- Batch runs and processed transaction count.
- Graph cache hit/miss and evidence completeness.
- Copilot answer/fallback status.

Operational signals:

- Rising `RAW_FALLBACK`: check provider health, timeout/circuit state and claim rejection metadata; investigation remains available.
- Batch watermark not advancing: inspect the persisted lease owner/expiry and `last_error`; do not manually delete findings.
- Repeated failure signature: group by `fsig-v1:*`, then compare first anomalous node and evidence completeness.
- Export/replay artifact missing: confirm retention expiry and object-storage health. Metadata is retained for audit even after object cleanup.

## Recovery procedures

### Batch worker lease appears stuck

Wait until `lease_until` expires. Restarting a service instance is safe because the lease and watermark are persisted. Do not move the watermark forward manually unless an incident change is approved and audited.

### AI provider unavailable

Leave the provider disabled or correct local/secret-store configuration. Users continue with raw structured evidence. Never paste production data into an external model console.

### Replay remains pending/running

Check the replay worker logs by run ID, object-storage reachability and runtime commit/image SHA. Retrying the same idempotency key returns the existing run. Do not point the runtime at production infrastructure.

### Evidence source unavailable

Treat the conclusion as partial. Restore the source service, then reload the investigation. Graph cache keys include the source watermark and expire automatically.

### Rollback

Disable forensics/replay/AI through environment configuration and restart the affected service. Flyway migrations are forward-only; do not delete forensic tables or posted journals. Money corrections use reversal journals.

## Data retention and disposal

The retention scheduler deletes object content first and marks metadata expired only after successful deletion. Copilot sessions/messages and expired graph cache entries are removed according to local policy. Posted journals, case history and audit records are never modified by retention cleanup.

## Deployment checklist

- Branch is not `main`; migrations are forward-only and have unique versions.
- Required local/secret-store properties exist; committed configuration contains no values or credentials.
- `common-lib`, `account-service`, `transaction-service` and Angular production builds succeed.
- Account and transaction migrations are validated on a clean database and an upgrade copy before production rollout.
- RBAC role assignments and maker-checker separation are reviewed.
- Object storage, internal account API and provider-off fallback are verified.
- The `Forensic scenario governance` path gate is required by branch protection for money/forensics changes.
- Only the infrastructure containers required by the operator workflow are started.
