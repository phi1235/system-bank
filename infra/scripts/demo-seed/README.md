# Demo seed data (local / non-prod only)

Loads realistic sample data so you can exercise IB + back-office flows without clicking through empty screens.

## Prerequisites

- Postgres container `bank-postgres` healthy (`127.0.0.1:5433`)
- Services already migrated (auth/customer/account/transaction/notification DBs exist)

## Run

From repo root (PowerShell):

```powershell
.\infra\scripts\seed-demo-data.ps1
```

Idempotent for fixed demo IDs (`ON CONFLICT DO UPDATE` / delete-by-prefix where needed). Safe to re-run.

## Demo logins

Password for **all seeded non-admin users**: `Demo1234!`

| Username     | Role(s)      | Notes                                      |
|--------------|--------------|--------------------------------------------|
| `testuser`   | CUSTOMER     | Existing local user; password reset to demo |
| `alice`      | CUSTOMER     | Rich accounts, beneficiaries, transfers    |
| `bob`        | CUSTOMER     | Counterparty for transfers                 |
| `carol`      | CUSTOMER     | One FROZEN account                         |
| `dave`       | CUSTOMER     | Smaller balance                            |
| `opsadmin`   | OPS_ADMIN    | Freeze / transfer monitor                  |
| `kyc1`       | KYC_OFFICER  | KYC decide                                 |
| `support1`   | SUPPORT      | Customer read                              |
| `auditor1`   | AUDITOR      | Audit read                                 |
| `lockeduser` | CUSTOMER     | `enabled=false` (login blocked)            |
| `mustchange` | CUSTOMER     | `must_change_password=true`                |

`admin` is **not** overwritten — keep `ADMIN_PASSWORD` from `infra/.env`.

## What gets seeded

- Auth users + sample audit + open password-reset ticket
- Customer profiles (mixed KYC)
- Payment/savings accounts, ledger history, fee-income residual
- Beneficiaries, transfer history (COMPLETED/FAILED) + saga steps
- Sample notification logs

## Security

- Demo passwords and fixed UUIDs are for **local** testing only.
- Do **not** run against shared/staging/prod without scrubbing.
