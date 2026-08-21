# Check Health of all 8 Bank System microservices

$services = @(
    @{ Name = "Discovery Server   "; Port = 8761; Path = "/actuator/health" },
    @{ Name = "API Gateway        "; Port = 8080; Path = "/actuator/health" },
    @{ Name = "Auth Service       "; Port = 8081; Path = "/actuator/health" },
    @{ Name = "Customer Service   "; Port = 8082; Path = "/actuator/health" },
    @{ Name = "Account Service    "; Port = 8083; Path = "/actuator/health" },
    @{ Name = "Transaction Service"; Port = 8084; Path = "/actuator/health" },
    @{ Name = "Notification Serv. "; Port = 8085; Path = "/actuator/health" },
    @{ Name = "Corporate Service  "; Port = 8088; Path = "/actuator/health" }
)

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "       Bank System - Microservices Health Check      " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

foreach ($s in $services) {
    $port = $s.Port
    $name = $s.Name
    $path = $s.Path
    $url = "http://localhost:$port$path"
    
    try {
        $response = Invoke-RestMethod -Uri $url -TimeoutSec 2 -ErrorAction Stop
        $status = if ($response.status) { $response.status } else { "UP" }
        Write-Host "  [$port] $name : " -NoNewline
        Write-Host "OK ($status)" -ForegroundColor Green
    } catch {
        $tcp = Test-NetConnection -ComputerName "localhost" -Port $port -WarningAction SilentlyContinue
        if ($tcp.TcpTestSucceeded) {
            Write-Host "  [$port] $name : " -NoNewline
            Write-Host "OK (LISTENING)" -ForegroundColor Green
        } else {
            Write-Host "  [$port] $name : " -NoNewline
            Write-Host "FAILED / NOT READY" -ForegroundColor Red
        }
    }
}

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "Eureka Dashboard : http://localhost:8761" -ForegroundColor Yellow
Write-Host "API Gateway      : http://localhost:8080" -ForegroundColor Yellow
Write-Host "=====================================================" -ForegroundColor Cyan
