# Bank System - 8 Microservices Lightweight Runner

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
if (-not $scriptDir) { $scriptDir = $PSScriptRoot }
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
Set-Location $repoRoot

Write-Host "=====================================================" -ForegroundColor Green
Write-Host "  Bank System - 8 Microservices Lightweight Runner   " -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Green

# 1. Check Docker Infra (Postgres, Redis, Kafka)
Write-Host ""
Write-Host "[1/3] Checking Dev Infrastructure..." -ForegroundColor Cyan
$pg = docker ps --filter "name=bank-postgres" --format "{{.Names}}"
if (-not $pg) {
    Write-Host "Starting Dev Infrastructure (Postgres, Redis, Kafka)..." -ForegroundColor Yellow
    docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d
} else {
    Write-Host "Dev Infrastructure is already running." -ForegroundColor Green
}

# 2. Define 8 services in dependency startup order
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

# 3. Launch each service
Write-Host ""
Write-Host "[2/3] Launching 8 backend services (Max RAM: 256MB each)..." -ForegroundColor Cyan

foreach ($s in $services) {
    $serviceDir = "$repoRoot\backend\$s"
    $jarPath = "$serviceDir\target\$s-0.1.0-SNAPSHOT.jar"
    
    if (-not (Test-Path $jarPath)) {
        $found = Get-ChildItem -Path "$serviceDir\target" -Filter "$s-*.jar" -Exclude "*sources.jar","*javadoc.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $jarPath = $found.FullName }
    }

    if (Test-Path $jarPath) {
        Write-Host "  -> Starting $s (JAR: $(Split-Path -Leaf $jarPath))..." -ForegroundColor Green
        $cmdArg = "/k title $s && cd /d `"$serviceDir`" && java -Xms64m -Xmx256m -XX:+UseSerialGC -Dspring.jmx.enabled=false -Dspring.profiles.active=local -jar `"$jarPath`""
        Start-Process cmd.exe -ArgumentList $cmdArg
    } else {
        Write-Host "  -> [ERROR] JAR file not found at: $jarPath" -ForegroundColor Red
    }
    
    if ($s -eq "discovery-server") {
        Start-Sleep -Seconds 4
    } else {
        Start-Sleep -Seconds 1
    }
}

Write-Host ""
Write-Host "[3/3] All 8 microservices have been launched successfully!" -ForegroundColor Green
Write-Host "  - Eureka Dashboard : http://localhost:8761" -ForegroundColor White
Write-Host "  - API Gateway      : http://localhost:8080" -ForegroundColor White
Write-Host "  - Angular Frontend : http://localhost:4200" -ForegroundColor White
Write-Host "=====================================================" -ForegroundColor Green
