#!/usr/bin/env bash
# Create logical DBs for bank-system (ADR-007: multi-DB one Postgres).
# Safe to re-run: skips DBs that already exist.
#
# Usage (from repo root or anywhere):
#   ./infra/scripts/init-databases.sh
#   ./infra/scripts/init-databases.sh --docker          # via bank-postgres container
#   ./infra/scripts/init-databases.sh --host localhost --port 5433 --user bank --password bank
#
# Docker first-time: compose already mounts
#   infra/postgres/init-databases.sql → /docker-entrypoint-initdb.d/
# and runs it once when volume is empty. Use this script when:
#   - volume already existed without DBs
#   - local Postgres (not container)
#   - you need to recreate DBs after wipe

set -euo pipefail

MODE="auto" # auto | docker | host
HOST="${DB_HOST:-localhost}"
PORT="${DB_PORT:-5433}"
USER="${POSTGRES_USER:-bank}"
PASSWORD="${POSTGRES_PASSWORD:-bank}"
CONTAINER="${POSTGRES_CONTAINER:-bank-postgres}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --docker) MODE="docker"; shift ;;
    --host) MODE="host"; HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --user) USER="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --container) CONTAINER="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

DBS=(bank_auth bank_customer bank_account bank_transaction bank_notification)

run_sql() {
  local sql="$1"
  if [[ "$MODE" == "docker" ]] || { [[ "$MODE" == "auto" ]] && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$CONTAINER"; }; then
    docker exec -i -e PGPASSWORD="$PASSWORD" "$CONTAINER" \
      psql -U "$USER" -d postgres -v ON_ERROR_STOP=1 -c "$sql"
  else
    if ! command -v psql >/dev/null 2>&1; then
      echo "ERROR: psql not found and container '$CONTAINER' not running."
      echo "Start stack: docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres"
      echo "Or install client and use --host/--port."
      exit 1
    fi
    PGPASSWORD="$PASSWORD" psql -h "$HOST" -p "$PORT" -U "$USER" -d postgres -v ON_ERROR_STOP=1 -c "$sql"
  fi
}

echo "==> Creating databases (user=$USER) ..."
for db in "${DBS[@]}"; do
  exists=$(run_sql "SELECT 1 FROM pg_database WHERE datname = '$db';" | tr -d '[:space:]' || true)
  # run_sql prints table; simpler: try CREATE with IF NOT EXISTS via DO block
  run_sql "SELECT 'CREATE DATABASE $db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec" 2>/dev/null \
    || run_sql "
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db') THEN
    PERFORM dblink_exec('dbname=postgres', 'CREATE DATABASE $db');
  END IF;
EXCEPTION WHEN OTHERS THEN
  -- dblink may be missing; fallback CREATE and ignore duplicate
  NULL;
END
\$\$;
" 2>/dev/null || true

  # Portable create (ignore error if exists)
  set +e
  out=$(run_sql "CREATE DATABASE $db;" 2>&1)
  rc=$?
  set -e
  if [[ $rc -eq 0 ]]; then
    echo "  + created $db"
  elif echo "$out" | grep -qi 'already exists'; then
    echo "  = exists  $db"
  else
    echo "  ! $db: $out"
    exit 1
  fi
done

echo "==> Done. Schema (tables) is applied by each service Flyway on first start."
echo "    Next: docker compose -f infra/docker-compose.yml --env-file infra/.env up -d"
echo "    Or wait for auth/customer/account/transaction/notification health."
