#Requires -Version 5.1
# Load local demo data into system-bank Postgres DBs.
# Idempotent for fixed demo UUIDs. Does not overwrite admin password.
# Seeded non-admin password: Demo1234!
$ErrorActionPreference = 'Stop'

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$EnvFile = Join-Path $RepoRoot 'infra\.env'
$SeedDir = Join-Path $PSScriptRoot 'demo-seed'
$Container = if ($env:BANK_POSTGRES_CONTAINER) { $env:BANK_POSTGRES_CONTAINER } else { 'bank-postgres' }

if (-not (Test-Path $EnvFile)) {
  throw "Missing env file: $EnvFile"
}
if (-not (Test-Path $SeedDir)) {
  throw "Missing seed dir: $SeedDir"
}

function Get-EnvValue([string]$Key) {
  $line = Get-Content $EnvFile | Where-Object { $_ -match ("^" + [regex]::Escape($Key) + "=") } | Select-Object -First 1
  if (-not $line) { return $null }
  return $line.Substring($Key.Length + 1)
}

$PgUser = Get-EnvValue 'POSTGRES_USER'
if (-not $PgUser) { $PgUser = 'bank' }

$running = docker ps --format '{{.Names}}' | Where-Object { $_ -eq $Container }
if (-not $running) {
  throw "Container $Container is not running. Start infra first."
}

function Invoke-SqlFile([string]$Db, [string]$File) {
  $name = Split-Path $File -Leaf
  Write-Host (">> {0} :: {1}" -f $Db, $name) -ForegroundColor Cyan
  Get-Content -Raw -Path $File | docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $PgUser -d $Db | Out-Host
}

Write-Host ("Seeding demo data into {0} (user={1})..." -f $Container, $PgUser) -ForegroundColor Green
Invoke-SqlFile 'bank_auth'         (Join-Path $SeedDir '01_auth.sql')
Invoke-SqlFile 'bank_customer'     (Join-Path $SeedDir '02_customer.sql')
Invoke-SqlFile 'bank_account'      (Join-Path $SeedDir '03_account.sql')
Invoke-SqlFile 'bank_transaction'  (Join-Path $SeedDir '04_transaction.sql')
Invoke-SqlFile 'bank_notification' (Join-Path $SeedDir '05_notification.sql')

Write-Host ''
Write-Host 'Counts:' -ForegroundColor Green
docker exec $Container psql -U $PgUser -d bank_auth -c 'SELECT count(*) AS users FROM users;'
docker exec $Container psql -U $PgUser -d bank_customer -c 'SELECT count(*) AS customers FROM customers;'
docker exec $Container psql -U $PgUser -d bank_account -c 'SELECT count(*) AS accounts FROM accounts; SELECT count(*) AS ledger FROM ledger_entries;'
docker exec $Container psql -U $PgUser -d bank_transaction -c 'SELECT count(*) AS transfers FROM transfer_orders; SELECT count(*) AS beneficiaries FROM beneficiaries;'
docker exec $Container psql -U $PgUser -d bank_notification -c 'SELECT count(*) AS notifications FROM notification_logs;'

Write-Host ''
Write-Host 'Demo password for seeded users: Demo1234!' -ForegroundColor Yellow
Write-Host 'Try: alice / bob / testuser / opsadmin / kyc1  (admin password stays in infra/.env)' -ForegroundColor Yellow
Write-Host 'Done.' -ForegroundColor Green
