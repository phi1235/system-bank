param(
    [string]$ServiceName
)

$services = @(
    "discovery-server",
    "api-gateway",
    "auth-service",
    "customer-service",
    "account-service",
    "transaction-service",
    "notification-service",
    "corporate-service"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
if (-not $scriptDir) { $scriptDir = $PSScriptRoot }
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path

# If no service name passed, show interactive menu
if (-not $ServiceName) {
    Write-Host "=====================================================" -ForegroundColor Cyan
    Write-Host "         Bank System - Restart Single Service        " -ForegroundColor Cyan
    Write-Host "=====================================================" -ForegroundColor Cyan
    for ($i = 0; $i -lt $services.Count; $i++) {
        Write-Host "  $($i + 1). $($services[$i])" -ForegroundColor White
    }
    Write-Host "=====================================================" -ForegroundColor Cyan
    $choice = Read-Host "Select service number to restart (1-8)"
    $idx = [int]$choice - 1
    if ($idx -ge 0 -and $idx -lt $services.Count) {
        $ServiceName = $services[$idx]
    } else {
        Write-Host "Invalid selection. Exiting." -ForegroundColor Red
        return
    }
}

if ($services -notcontains $ServiceName) {
    Write-Host "[ERROR] Unknown service: $ServiceName" -ForegroundColor Red
    Write-Host "Available services: $($services -join ', ')" -ForegroundColor Yellow
    return
}

Write-Host "`n[1/3] Stopping existing instance of $ServiceName..." -ForegroundColor Yellow
$procs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.CommandLine -like "*$ServiceName*"
}
foreach ($p in $procs) {
    Write-Host "  -> Terminating process ID $($p.ProcessId)..." -ForegroundColor Red
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 1

Write-Host "[2/3] Rebuilding $ServiceName..." -ForegroundColor Cyan
$mvnPath = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"
if (-not (Test-Path $mvnPath)) {
    $mvnPath = "mvn.cmd"
}

$serviceDir = "$repoRoot\backend\$ServiceName"
Push-Location $serviceDir
& "$mvnPath" package -DskipTests
Pop-Location

Write-Host "[3/3] Starting $ServiceName..." -ForegroundColor Green
$jarPath = "$serviceDir\target\$ServiceName-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    $found = Get-ChildItem -Path "$serviceDir\target" -Filter "$ServiceName-*.jar" -Exclude "*sources.jar","*javadoc.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $jarPath = $found.FullName }
}

$cmdArg = "/k title $ServiceName && cd /d `"$serviceDir`" && java -Xms64m -Xmx256m -XX:+UseSerialGC -Dspring.jmx.enabled=false -Dspring.profiles.active=local -jar `"$jarPath`""
Start-Process cmd.exe -ArgumentList $cmdArg

Write-Host "Service $ServiceName restarted in a new terminal window!`n" -ForegroundColor Green
