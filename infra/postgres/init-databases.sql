-- Logical database-per-service (ADR-007: one Postgres, multiple DBs)
-- Auto-run on FIRST container start when volume is empty
-- (docker-entrypoint-initdb.d — see docker-compose.yml).
--
-- Re-run / machine already has volume: use
--   ./infra/scripts/init-databases.sh

CREATE DATABASE bank_auth;
CREATE DATABASE bank_customer;
CREATE DATABASE bank_account;
CREATE DATABASE bank_transaction;
CREATE DATABASE bank_notification;
