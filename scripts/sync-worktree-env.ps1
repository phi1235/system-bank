# PowerShell script to automatically synchronize local gitignored configuration files into a Git Worktree
param (
    [string]$SourceRoot = ""
)

$ErrorActionPreference = "Stop"

# Determine Current Worktree Root
$CurrentDir = (Get-Location).Path
try {
    $CurrentWorktree = (git rev-parse --show-toplevel 2>$null).Trim()
    if ($CurrentWorktree) { $CurrentDir = $CurrentWorktree }
} catch {}

# Determine Main Root Repository
if (-not $SourceRoot) {
    try {
        $worktrees = git worktree list --porcelain
        $firstLine = ($worktrees | Select-String -Pattern "^worktree " | Select-Object -First 1).Line
        if ($firstLine) {
            $SourceRoot = $firstLine.Substring("worktree ".Length).Trim()
        }
    } catch {}
}

if (-not $SourceRoot -or -not (Test-Path $SourceRoot)) {
    $fallback = "C:\Users\TuanNQ3\system-bank"
    if (Test-Path $fallback) {
        $SourceRoot = $fallback
    } else {
        Write-Error "Could not determine main repository root directory."
        exit 1
    }
}

if ($CurrentDir.TrimEnd('\') -ieq $SourceRoot.TrimEnd('\')) {
    Write-Host "[INFO] Current directory is the main repository root ($SourceRoot). No sync needed." -ForegroundColor Cyan
    exit 0
}

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Syncing Local Configs from Root: $SourceRoot" -ForegroundColor Green
Write-Host "To Current Worktree:             $CurrentDir" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

# 1. Sync infra/.env*
$sourceInfra = Join-Path $SourceRoot "infra"
$targetInfra = Join-Path $CurrentDir "infra"
if (Test-Path $sourceInfra) {
    if (-not (Test-Path $targetInfra)) { New-Item -ItemType Directory -Path $targetInfra -Force | Out-Null }
    Get-ChildItem -Path $sourceInfra -Filter ".env*" -Force | ForEach-Object {
        $dest = Join-Path $targetInfra $_.Name
        Copy-Item -Path $_.FullName -Destination $dest -Force
        Write-Host " [x] Synced $($_.Name) -> infra/" -ForegroundColor Gray
    }
}

# 2. Sync application-local.yml across all backend microservices
$sourceBackend = Join-Path $SourceRoot "backend"
$targetBackend = Join-Path $CurrentDir "backend"
if (Test-Path $sourceBackend) {
    Get-ChildItem -Path $sourceBackend -Filter "application-local.yml" -Recurse | ForEach-Object {
        $relPath = $_.FullName.Substring($sourceBackend.Length).TrimStart('\', '/')
        $destPath = Join-Path $targetBackend $relPath
        $destDir = Split-Path -Parent $destPath
        if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
        Copy-Item -Path $_.FullName -Destination $destPath -Force
        Write-Host " [x] Synced $relPath" -ForegroundColor Gray
    }
}

# 3. Create Junction for Frontend node_modules if missing
$sourceNodeModules = Join-Path $SourceRoot "frontend\bank-angular-app\node_modules"
$targetNodeModules = Join-Path $CurrentDir "frontend\bank-angular-app\node_modules"
if ((Test-Path $sourceNodeModules) -and (-not (Test-Path $targetNodeModules))) {
    $parentDir = Split-Path -Parent $targetNodeModules
    if (-not (Test-Path $parentDir)) { New-Item -ItemType Directory -Path $parentDir -Force | Out-Null }
    Write-Host " [*] Creating directory junction for node_modules..." -ForegroundColor Yellow
    cmd.exe /c "mklink /J `"$targetNodeModules`" `"$sourceNodeModules`"" | Out-Null
    Write-Host " [x] Linked node_modules junction successfully." -ForegroundColor Gray
}

# 4. Sync .agents and .codegraph
$sourceAgents = Join-Path $SourceRoot ".agents"
$targetAgents = Join-Path $CurrentDir ".agents"
if (Test-Path $sourceAgents) {
    Copy-Item -Path $sourceAgents -Destination $targetAgents -Recurse -Force
    Write-Host " [x] Synced .agents/" -ForegroundColor Gray
}

$sourceCodegraph = Join-Path $SourceRoot ".codegraph"
$targetCodegraph = Join-Path $CurrentDir ".codegraph"
if (Test-Path $sourceCodegraph) {
    Copy-Item -Path $sourceCodegraph -Destination $targetCodegraph -Recurse -Force
    Write-Host " [x] Synced .codegraph/" -ForegroundColor Gray
}

Write-Host "`nAll local configurations successfully synced to worktree!" -ForegroundColor Green
