# Apply P2 support-ticket Flyway SQL to live Postgres (when Docker/Flyway service not running).
# Idempotent: CREATE IF NOT EXISTS / ON CONFLICT DO NOTHING.
# Usage (from repo root):
#   powershell -File infra/scripts/apply-p2-support-ticket-migrations.ps1
# Requires: Docker container bank-postgres running, or local psql on POSTGRES_HOST_PORT.

$ErrorActionPreference = 'Stop'
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$EnvFile = Join-Path $RepoRoot 'infra\.env'
if (-not (Test-Path $EnvFile)) {
  throw "Missing infra/.env - copy from infra/.env.example and fill values first."
}

function Get-DotEnvValue([string]$path, [string]$key) {
  $line = Get-Content $path | Where-Object { $_ -match "^\s*$key\s*=" } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -replace "^\s*$key\s*=\s*", '').Trim().Trim('"').Trim("'")
}

$pgUser = Get-DotEnvValue $EnvFile 'POSTGRES_USER'
if (-not $pgUser) { $pgUser = 'bank' }
$pgPass = Get-DotEnvValue $EnvFile 'POSTGRES_PASSWORD'
if (-not $pgPass) { throw 'POSTGRES_PASSWORD missing in infra/.env' }
$hostPort = Get-DotEnvValue $EnvFile 'POSTGRES_HOST_PORT'
if (-not $hostPort) { $hostPort = '5433' }
$customerDb = Get-DotEnvValue $EnvFile 'CUSTOMER_DB_NAME'
if (-not $customerDb) { $customerDb = 'bank_customer' }
$authDb = Get-DotEnvValue $EnvFile 'AUTH_DB_NAME'
if (-not $authDb) { $authDb = 'bank_auth' }

$customerSql = Join-Path $RepoRoot 'backend\customer-service\src\main\resources\db\migration\V2__support_tickets.sql'
$authSqlV9 = Join-Path $RepoRoot 'backend\auth-service\src\main\resources\db\migration\V9__support_ticket_permissions.sql'
$authSqlV10 = Join-Path $RepoRoot 'backend\auth-service\src\main\resources\db\migration\V10__support_ticket_maker_checker.sql'
$authSqlV11 = Join-Path $RepoRoot 'backend\auth-service\src\main\resources\db\migration\V11__ib_support_create_permission.sql'
foreach ($f in @($customerSql, $authSqlV9, $authSqlV10, $authSqlV11)) {
  if (-not (Test-Path $f)) { throw "Missing migration file: $f" }
}

function Invoke-SqlFile([string]$db, [string]$sqlPath, [string]$label) {
  Write-Host "==> $label -> $db"
  $sql = Get-Content -Raw -Path $sqlPath
  $container = docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq 'bank-postgres' }
  if ($container) {
    $sql | docker exec -i -e "PGPASSWORD=$pgPass" bank-postgres `
      psql -v ON_ERROR_STOP=1 -U $pgUser -d $db
  } else {
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
      throw "Neither docker container bank-postgres nor local psql available. Start postgres first."
    }
    $env:PGPASSWORD = $pgPass
    try {
      & psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $hostPort -U $pgUser -d $db -f $sqlPath
    } finally {
      Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
  }
  Write-Host "OK $label"
}

function Ensure-FlywayHistory([string]$db, [string]$version, [string]$description, [string]$script, [Nullable[int]]$checksum = $null) {
  # If services later run Flyway, mark version applied so Flyway does not re-run.
  # checksum must match Flyway's CRC32 of the migration file (not NULL).
  # Only insert when flyway_schema_history exists and version is missing.
  $checksumSql = if ($null -eq $checksum) { 'NULL' } else { [string]$checksum }
  $checkSql = @"
DO `$`$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
  ) AND NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '$version'
  ) THEN
    INSERT INTO flyway_schema_history (
      installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
    )
    SELECT
      COALESCE(MAX(installed_rank), 0) + 1,
      '$version',
      '$description',
      'SQL',
      '$script',
      $checksumSql,
      current_user,
      NOW(),
      0,
      TRUE
    FROM flyway_schema_history;
    RAISE NOTICE 'flyway_schema_history: recorded version $version checksum=$checksumSql';
  ELSIF EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '$version' AND checksum IS NULL AND $checksumSql IS NOT NULL
  ) THEN
    UPDATE flyway_schema_history SET checksum = $checksumSql WHERE version = '$version';
    RAISE NOTICE 'flyway_schema_history: repaired checksum for version $version -> $checksumSql';
  ELSE
    RAISE NOTICE 'flyway_schema_history: skip record version $version (missing table or already present)';
  END IF;
END
`$`$;
"@
  Write-Host "==> flyway history $version on $db"
  $container = docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq 'bank-postgres' }
  if ($container) {
    $checkSql | docker exec -i -e "PGPASSWORD=$pgPass" bank-postgres `
      psql -v ON_ERROR_STOP=1 -U $pgUser -d $db
  } else {
    $env:PGPASSWORD = $pgPass
    try {
      $checkSql | & psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $hostPort -U $pgUser -d $db
    } finally {
      Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
  }
}

Invoke-SqlFile $customerDb $customerSql 'V2 support_tickets'
# Checksums from Flyway validate log (CRC32 of migration content).
Ensure-FlywayHistory $customerDb '2' 'support tickets' 'V2__support_tickets.sql' 2009433375

Invoke-SqlFile $authDb $authSqlV9 'V9 support ticket permissions'
Ensure-FlywayHistory $authDb '9' 'support ticket permissions' 'V9__support_ticket_permissions.sql' -502167712

Invoke-SqlFile $authDb $authSqlV10 'V10 support ticket maker-checker'
Ensure-FlywayHistory $authDb '10' 'support ticket maker checker' 'V10__support_ticket_maker_checker.sql' 1052561436

Invoke-SqlFile $authDb $authSqlV11 'V11 ib support create permission'
Ensure-FlywayHistory $authDb '11' 'ib support create permission' 'V11__ib_support_create_permission.sql' 264219538

Write-Host ''
Write-Host 'Done. Verify:'
Write-Host "  support_tickets table in $customerDb"
Write-Host "  permissions support:tickets:list|claim|decide + ib:support:view|create in $authDb"
