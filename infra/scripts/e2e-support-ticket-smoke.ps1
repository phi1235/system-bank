# API smoke: support ticket create → staff resolve/reject (no maker-checker).
# Requires: stack up (gateway + auth + customer), seed users.
# Usage (repo root):
#   $env:CUSTOMER_PASS='...'; $env:STAFF_PASS='...'
#   powershell -File infra/scripts/e2e-support-ticket-smoke.ps1
# Env:
#   GATEWAY_URL (default http://localhost:8080)
#   CUSTOMER_USER / CUSTOMER_PASS (required)
#   STAFF_USER / STAFF_PASS (required) — any staff with support:tickets:decide
# Back-compat: MAKER_PASS / CHECKER_PASS still accepted as STAFF_PASS fallback.

$ErrorActionPreference = 'Stop'
$Gateway = if ($env:GATEWAY_URL) { $env:GATEWAY_URL.TrimEnd('/') } else { 'http://localhost:8080' }
$CustomerUser = if ($env:CUSTOMER_USER) { $env:CUSTOMER_USER } else { 'customer1' }
$CustomerPass = $env:CUSTOMER_PASS
$StaffUser = if ($env:STAFF_USER) { $env:STAFF_USER } elseif ($env:MAKER_USER) { $env:MAKER_USER } else { 'admin' }
$StaffPass = if ($env:STAFF_PASS) { $env:STAFF_PASS } elseif ($env:CHECKER_PASS) { $env:CHECKER_PASS } else { $env:MAKER_PASS }

if (-not $CustomerPass -or -not $StaffPass) {
  throw 'Set CUSTOMER_PASS and STAFF_PASS env vars (or MAKER_PASS/CHECKER_PASS). No password defaults in script.'
}

function Invoke-Json {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [int[]]$OkStatuses = @(200, 201)
  )
  $params = @{
    Method      = $Method
    Uri         = $Url
    Headers     = $Headers
    ContentType = 'application/json'
  }
  if ($null -ne $Body) {
    $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  try {
    $resp = Invoke-WebRequest @params -UseBasicParsing
  } catch {
    $r = $_.Exception.Response
    if ($r) {
      $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
      $text = $reader.ReadToEnd()
      throw "HTTP $([int]$r.StatusCode) $Method $Url :: $text"
    }
    throw
  }
  if ($OkStatuses -notcontains [int]$resp.StatusCode) {
    throw "Unexpected status $([int]$resp.StatusCode) for $Method $Url body=$($resp.Content)"
  }
  if ([string]::IsNullOrWhiteSpace($resp.Content)) { return $null }
  return $resp.Content | ConvertFrom-Json
}

function Login([string]$username, [string]$password) {
  Write-Host "Login $username ..."
  $res = Invoke-Json -Method POST -Url "$Gateway/api/v1/auth/login" -Body @{
    username = $username
    password = $password
  }
  $data = if ($res.data) { $res.data } else { $res }
  if ($data.mfaRequired) {
    throw "User $username requires MFA - use a non-MFA smoke account"
  }
  if (-not $data.accessToken) {
    throw "No accessToken for $username"
  }
  return $data.accessToken
}

function AuthHeaders([string]$token) {
  return @{ Authorization = "Bearer $token" }
}

function Unwrap($res) {
  if ($null -eq $res) { return $null }
  if ($res.PSObject.Properties.Name -contains 'data') { return $res.data }
  return $res
}

Write-Host "Gateway: $Gateway"
$customerToken = Login $CustomerUser $CustomerPass
$staffToken = Login $StaffUser $StaffPass

$subject = "e2e-smoke-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Write-Host "Create ticket as customer ($subject) ..."
$created = Unwrap (Invoke-Json -Method POST `
    -Url "$Gateway/api/v1/customers/me/support-tickets" `
    -Headers (AuthHeaders $customerToken) `
    -Body @{
      category = 'GENERAL'
      subject  = $subject
      body     = 'E2E smoke: staff may resolve OPEN ticket directly (no claim required).'
      priority = 'NORMAL'
    })
$ticketId = $created.id
if (-not $ticketId) { throw "Create failed: no ticket id" }
Write-Host "  ticketId=$ticketId status=$($created.status)"
if ($created.status -ne 'OPEN') { throw "Expected OPEN, got $($created.status)" }

Write-Host "Customer cannot resolve own ticket ..."
$selfBlocked = $false
try {
  Invoke-Json -Method POST `
    -Url "$Gateway/api/v1/admin/support-tickets/$ticketId/resolve" `
    -Headers (AuthHeaders $customerToken) `
    -Body @{ resolutionNote = 'self' } | Out-Null
} catch {
  $selfBlocked = $true
  Write-Host "  OK blocked requester self-resolve"
}
if (-not $selfBlocked) { throw 'Expected customer self-resolve to fail' }

Write-Host "Staff resolves OPEN ticket directly (no claim) ..."
$resolved = Unwrap (Invoke-Json -Method POST `
    -Url "$Gateway/api/v1/admin/support-tickets/$ticketId/resolve" `
    -Headers (AuthHeaders $staffToken) `
    -Body @{ resolutionNote = 'E2E resolved OK' })
if ($resolved.status -ne 'RESOLVED') { throw "Expected RESOLVED, got $($resolved.status)" }
Write-Host "  resolvedBy=$($resolved.resolvedBy) assignedTo=$($resolved.assignedTo)"

Write-Host "Customer sees RESOLVED ..."
$mine = Unwrap (Invoke-Json -Method GET `
    -Url "$Gateway/api/v1/customers/me/support-tickets/$ticketId" `
    -Headers (AuthHeaders $customerToken))
if ($mine.status -ne 'RESOLVED') { throw "Customer view expected RESOLVED, got $($mine.status)" }

# Optional claim path: create another ticket, claim then same staff resolve
$subject2 = "e2e-claim-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Write-Host "Optional claim path: create + claim + same staff resolve ($subject2) ..."
$created2 = Unwrap (Invoke-Json -Method POST `
    -Url "$Gateway/api/v1/customers/me/support-tickets" `
    -Headers (AuthHeaders $customerToken) `
    -Body @{
      category = 'ACCOUNT'
      subject  = $subject2
      body     = 'E2E optional claim then same staff resolve.'
      priority = 'LOW'
    })
$ticketId2 = $created2.id
$claimed = Unwrap (Invoke-Json -Method POST `
    -Url "$Gateway/api/v1/admin/support-tickets/$ticketId2/claim" `
    -Headers (AuthHeaders $staffToken))
if ($claimed.status -ne 'IN_PROGRESS') { throw "Expected IN_PROGRESS after claim, got $($claimed.status)" }
$resolved2 = Unwrap (Invoke-Json -Method POST `
    -Url "$Gateway/api/v1/admin/support-tickets/$ticketId2/resolve" `
    -Headers (AuthHeaders $staffToken) `
    -Body @{ resolutionNote = 'claimed then resolved by same staff' })
if ($resolved2.status -ne 'RESOLVED') { throw "Expected RESOLVED after claim+resolve, got $($resolved2.status)" }
Write-Host "  claim+same-staff-resolve OK ticketId=$ticketId2"

Write-Host ''
Write-Host 'PASS e2e support ticket smoke (direct decide + optional claim)'
Write-Host "  ticketId=$ticketId subject=$subject"
Write-Host "  ticketId2=$ticketId2 subject=$subject2"
